/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.application.impl;

import io.vertx.core.*;
import io.vertx.core.application.HookContext;
import io.vertx.core.application.VertxApplication;
import io.vertx.core.application.VertxApplicationHooks;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static picocli.CommandLine.Parameters.NULL_VALUE;

@Command(name = "VertxApplication", description = "Runs a Vert.x application.", sortOptions = false)
public class VertxApplicationCommand implements Callable<Integer> {

  private static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  private static final String VERTX_EVENTBUS_PROP_PREFIX = "vertx.eventBus.options.";
  private static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  private static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  private static final int VERTX_INITIALIZATION_EXIT_CODE = 11;
  private static final int VERTX_DEPLOYMENT_EXIT_CODE = 15;

  @Option(
    names = {"-options", "--options", "-vertx-options", "--vertx-options"},
    description = {
      "Specifies the Vert.x options.",
      "It should reference either a JSON file which represents the options OR be a JSON string."
    },
    defaultValue = Option.NULL_VALUE
  )
  private String vertxOptions;

  @Option(
    names = {"-c", "-cluster", "--cluster"},
    description = {
      "If specified, then the Vert.x instance will form a cluster with any other Vert.x instances on the network."
    },
    arity = "0"
  )
  private Boolean clustered;
  @Option(
    names = {"-cluster-port", "--cluster-port"},
    description = {
      "Port to use for cluster communication.",
      "By default, a spare random port is chosen."
    }
  )
  private Integer clusterPort;
  @Option(
    names = {"-cluster-host", "--cluster-host"},
    description = {
      "Host to bind to for cluster communication.",
      "If this is not specified, Vert.x will attempt to choose one from the available interfaces."
    }
  )
  private String clusterHost;
  @Option(
    names = {"-cluster-public-port", "--cluster-public-port"},
    description = {
      "Public port to use for cluster communication.",
      "By default, Vert.x uses the same as the cluster port."
    }
  )
  private Integer clusterPublicPort;
  @Option(
    names = {"-cluster-public-host", "--cluster-public-host"},
    description = {
      "Public host to bind to for cluster communication.",
      "By default, Vert.x uses the same as the cluster host."
    }
  )
  private String clusterPublicHost;

  @Option(
    names = {"-deployment-options", "--deployment-options"},
    description = {
      "Specifies the main verticle deployment options."
    }
  )
  private String deploymentOptions;

  @Option(
    names = {"-w", "-worker", "--worker"},
    description = {
      "If specified, then the main verticle is deployed as a worker verticle.",
      "Takes precedences over the value defined in deployment options.",
    },
    arity = "0"
  )
  private Boolean worker;
  @Option(
    names = {"-instances", "--instances"},
    description = {
      "Specifies how many instances of the verticle will be deployed.",
      "Takes precedences over the value defined in deployment options."
    }
  )
  private Integer instances;

  @Option(
    names = {"-conf", "--conf"},
    description = {
      "Specifies configuration that should be provided to the verticle.",
      "It should reference either a JSON file which represents the options OR be a JSON string."
    }
  )
  private String config;

  @Option(
    names = {"-h", "-help", "--help"},
    usageHelp = true,
    description = {
      "Display a help message."
    },
    arity = "0")
  private boolean helpRequested;

  @Parameters(
    index = "0",
    description = {
      "The main verticle fully qualified class name."
    },
    defaultValue = NULL_VALUE
  )
  private String mainVerticle;

  private final VertxApplication vertxApplication;
  private final VertxApplicationHooks hooks;
  private final Logger log;

  private volatile VertxInternal vertx;
  private volatile HookContext hookContext;

  public VertxApplicationCommand(VertxApplication vertxApplication, VertxApplicationHooks hooks, Logger log) {
    this.vertxApplication = vertxApplication;
    this.hooks = hooks;
    this.log = log;
  }

  @Override
  public Integer call() {
    JsonObject optionsJson = readJsonFileOrString("options", vertxOptions);
    VertxBuilder builder = optionsJson != null ? new VertxBuilder(optionsJson) : new VertxBuilder();
    if (clustered == Boolean.TRUE) {
      EventBusOptions eventBusOptions = builder.options().getEventBusOptions();
      if (clusterHost != null) {
        eventBusOptions.setHost(clusterHost);
      }
      if (clusterPort != null) {
        eventBusOptions.setPort(clusterPort);
      }
      if (clusterPublicHost != null) {
        eventBusOptions.setClusterPublicHost(clusterPublicHost);
      }
      if (clusterPublicPort != null) {
        eventBusOptions.setClusterPublicPort(clusterPublicPort);
      }
      configureFromSystemProperties(log, eventBusOptions, VERTX_EVENTBUS_PROP_PREFIX);
    }
    configureFromSystemProperties(log, builder.options(), VERTX_OPTIONS_PROP_PREFIX);
    if (builder.options().getMetricsOptions() != null) {
      configureFromSystemProperties(log, builder.options().getMetricsOptions(), METRICS_OPTIONS_PROP_PREFIX);
    }
    hookContext = HookContext.create(builder.options());
    hooks.beforeStartingVertx(hookContext);
    builder.init();

    AsyncResult<Vertx> arv;
    try {
      arv = withTCCLAwait(() -> createVertx(builder), Duration.ofMinutes(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Thread interrupted in startup");
      hooks.afterFailureToStartVertx(hookContext, e);
      return VERTX_INITIALIZATION_EXIT_CODE;
    }
    if (arv == null) {
      log.error("Timed out in starting clustered Vert.x");
      hooks.afterFailureToStartVertx(hookContext, null);
      return VERTX_INITIALIZATION_EXIT_CODE;
    }
    if (arv.failed()) {
      hooks.afterFailureToStartVertx(hookContext, arv.cause());
      return VERTX_INITIALIZATION_EXIT_CODE;
    }

    vertx = (VertxInternal) arv.result();
    hookContext = hookContext.vertxStarted(vertx);
    hooks.afterVertxStarted(hookContext);
    vertx.addCloseHook(this::beforeStoppingVertx);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));

    JsonObject deploymentOptionsJson = readJsonFileOrString("deploymentOptions", deploymentOptions);
    DeploymentOptions deploymentOptions = deploymentOptionsJson != null ? new DeploymentOptions(deploymentOptionsJson) : new DeploymentOptions();
    if (worker == Boolean.TRUE) {
      deploymentOptions.setWorker(true);
    }
    if (instances != null) {
      deploymentOptions.setInstances(instances);
    }
    JsonObject conf = readJsonFileOrString("conf", config);
    if (conf == null) {
      conf = new JsonObject();
    }
    if (hooks instanceof VertxApplicationHooksAdapter) {
      VertxApplicationHooksAdapter adapter = (VertxApplicationHooksAdapter) hooks;
      adapter.afterConfigParsed(conf);
    }
    deploymentOptions.setConfig(conf);
    configureFromSystemProperties(log, deploymentOptions, DEPLOYMENT_OPTIONS_PROP_PREFIX);
    Supplier<Future<String>> deployer;
    Supplier<Verticle> verticleSupplier = hooks.verticleSupplier();
    if (verticleSupplier == null) {
      String verticleName = computeVerticleName();
      if (verticleName == null) {
        log.error("If the <mainVerticle> parameter is not provided, the 'Main-Verticle' manifest attribute must be provided.");
        return VERTX_DEPLOYMENT_EXIT_CODE;
      }
      deployer = () -> vertx.deployVerticle(verticleName, deploymentOptions);
      hookContext = hookContext.readyToDeploy(verticleName, deploymentOptions);
    } else {
      deployer = () -> vertx.deployVerticle(verticleSupplier, deploymentOptions);
      hookContext = hookContext.readyToDeploy(null, deploymentOptions);
    }
    hooks.beforeDeployingVerticle(hookContext);

    AsyncResult<String> ard;
    String message = hookContext.deploymentOptions().isWorker() ? "deploying worker verticle" : "deploying verticle";
    try {
      ard = withTCCLAwait(deployer, Duration.ofMinutes(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Thread interrupted in " + message);
      hooks.afterFailureToDeployVerticle(hookContext, e);
      return VERTX_DEPLOYMENT_EXIT_CODE;
    }
    if (ard == null) {
      log.error("Timed out in " + message);
      hooks.afterFailureToDeployVerticle(hookContext, null);
      return VERTX_DEPLOYMENT_EXIT_CODE;
    }
    if (ard.failed()) {
      Throwable cause = ard.cause();
      hooks.afterFailureToDeployVerticle(hookContext, cause);
      log.error("Failed in " + message, cause);
      return VERTX_DEPLOYMENT_EXIT_CODE;
    }

    log.info("Succeeded in " + message);
    hookContext = hookContext.verticleDeployed(ard.result());
    hooks.afterVerticleDeployed(hookContext);

    return null;
  }

  private String computeVerticleName() {
    List<String> attributeNames = Arrays.asList("Main-Verticle", "Default-Verticle-Factory");
    Map<String, String> manifestAttributes;
    try {
      manifestAttributes = getAttributesFromManifest(attributeNames);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    String mainVerticleAttribute = manifestAttributes.get("Main-Verticle");
    String defaultVerticleFactory = manifestAttributes.get("Default-Verticle-Factory");
    String verticleName = mainVerticle != null ? mainVerticle : mainVerticleAttribute;
    if (defaultVerticleFactory != null && verticleName != null && verticleName.indexOf(':') == -1) {
      verticleName = defaultVerticleFactory + ":" + verticleName;
    }
    return verticleName;
  }

  private Map<String, String> getAttributesFromManifest(List<String> attributeNames) throws IOException {
    Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
    while (resources.hasMoreElements()) {
      try (InputStream stream = resources.nextElement().openStream()) {
        Manifest manifest = new Manifest(stream);
        Attributes attributes = manifest.getMainAttributes();
        String mainClassName = attributes.getValue("Main-Class");
        if (vertxApplication.getClass().getName().equals(mainClassName)) {
          Map<String, String> map = new HashMap<>();
          for (String attributeName : attributeNames) {
            String attributeValue = attributes.getValue(attributeName);
            if (attributeValue != null) {
              map.put(attributeName, attributeValue);
            }
          }
          return Collections.unmodifiableMap(map);
        }
      }
    }
    return Collections.emptyMap();
  }

  private void beforeStoppingVertx(Promise<Void> promise) {
    try {
      hooks.beforeStoppingVertx(hookContext);
      promise.complete();
    } catch (Exception e) {
      promise.fail(e);
    }
  }

  private Future<Vertx> createVertx(VertxBuilder builder) {
    try {
      if (clustered == Boolean.TRUE) {
        log.info("Starting clustering...");
        return builder.clusteredVertx().onFailure(t -> {
          log.error("Failed to form cluster", t);
        });
      } else {
        return Future.succeededFuture(builder.vertx());
      }
    } catch (Exception e) {
      log.error("Failed to create the Vert.x instance", e);
      return Future.failedFuture(e);
    }
  }

  private void shutdownHook() {
    CountDownLatch latch = new CountDownLatch(1);
    Future<Void> future = vertx.close().andThen(v -> latch.countDown());
    long remaining = Duration.ofMinutes(2).toMillis();
    long stop = System.currentTimeMillis() + remaining;
    boolean stopped = false, interrupted = false;
    while (true) {
      try {
        if (remaining >= 0) {
          if (latch.await(remaining, MILLISECONDS)) {
            stopped = true;
          }
        }
        break;
      } catch (InterruptedException e) {
        interrupted = true;
        remaining = stop - System.currentTimeMillis();
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
    if (!stopped) {
      log.error("Timed out waiting for Vert.x to be closed");
      hooks.afterFailureToStopVertx(hookContext, null);
    } else if (future.failed()) {
      log.error("Failure in stopping Vert.x", future.cause());
      hooks.afterFailureToStopVertx(hookContext, future.cause());
    } else {
      hooks.afterVertxStopped(hookContext);
    }
  }

  private static <T> AsyncResult<T> withTCCLAwait(Supplier<Future<T>> supplier, Duration duration) throws InterruptedException {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      Future<T> future = supplier.get().andThen(v -> latch.countDown());
      if (latch.await(duration.toMillis(), MILLISECONDS)) {
        return future;
      }
      return null;
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private JsonObject readJsonFileOrString(String optionName, String jsonFileOrString) {
    if (jsonFileOrString == null) {
      return null;
    }
    try {
      Path path = Paths.get(jsonFileOrString);
      byte[] bytes = Files.readAllBytes(path);
      return new JsonObject(Buffer.buffer(bytes));
    } catch (InvalidPathException | IOException | DecodeException ignored) {
    }
    try {
      return new JsonObject(jsonFileOrString);
    } catch (DecodeException ignored) {
    }
    log.warn("The " + optionName + " option does not point to an valid JSON file or is not a valid JSON object.");
    return null;
  }

  private static void configureFromSystemProperties(Logger log, Object options, String prefix) {
    Properties props = System.getProperties();
    Enumeration<?> e = props.propertyNames();
    while (e.hasMoreElements()) {
      String propName = (String) e.nextElement();
      String propVal = props.getProperty(propName);
      if (propName.startsWith(prefix)) {
        String fieldName = propName.substring(prefix.length());
        Method setter = getSetter(fieldName, options.getClass());
        if (setter == null) {
          log.warn("No such property to configure on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        Class<?> argType = setter.getParameterTypes()[0];
        Object arg;
        try {
          if (argType.equals(String.class)) {
            arg = propVal;
          } else if (argType.equals(int.class)) {
            arg = Integer.valueOf(propVal);
          } else if (argType.equals(long.class)) {
            arg = Long.valueOf(propVal);
          } else if (argType.equals(boolean.class)) {
            arg = Boolean.valueOf(propVal);
          } else if (argType.isEnum()) {
            arg = Enum.valueOf((Class<? extends Enum>) argType, propVal);
          } else {
            log.warn("Invalid type for setter: " + argType);
            continue;
          }
        } catch (IllegalArgumentException e2) {
          log.warn("Invalid argtype:" + argType + " on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        try {
          setter.invoke(options, arg);
        } catch (Exception ex) {
          throw new VertxException("Failed to invoke setter: " + setter, ex);
        }
      }
    }
  }

  private static Method getSetter(String fieldName, Class<?> clazz) {
    Method[] meths = clazz.getDeclaredMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).equalsIgnoreCase(meth.getName())) {
        return meth;
      }
    }

    // This set contains the overridden methods
    meths = clazz.getMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).equalsIgnoreCase(meth.getName())) {
        return meth;
      }
    }

    return null;
  }
}
