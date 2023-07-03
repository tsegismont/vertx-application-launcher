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
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.json.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static io.vertx.core.application.impl.Utils.*;
import static io.vertx.core.impl.launcher.commands.BareCommand.*;
import static picocli.CommandLine.Parameters.NULL_VALUE;

@Command(name = "VertxApplication", description = "Runs a Vert.x application.", sortOptions = false)
public class VertxApplicationCommand implements Runnable {

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
  public void run() {
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
      configureFromSystemProperties(eventBusOptions, VERTX_EVENTBUS_PROP_PREFIX);
    }
    configureFromSystemProperties(builder.options(), VERTX_OPTIONS_PROP_PREFIX);
    if (builder.options().getMetricsOptions() != null) {
      configureFromSystemProperties(builder.options().getMetricsOptions(), METRICS_OPTIONS_PROP_PREFIX);
    }
    hookContext = HookContext.create(builder.options());
    hooks.beforeStartingVertx(hookContext);
    builder.init();

    AsyncResult<Vertx> arv;
    try {
      arv = await(() -> create(builder), Duration.ofMinutes(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Thread interrupted in startup");
      hooks.afterFailureToStartVertx(hookContext, e);
      return;
    }
    if (arv == null) {
      log.error("Timed out in starting clustered Vert.x");
      hooks.afterFailureToStartVertx(hookContext, null);
      return;
    }
    if (arv.failed()) {
      hooks.afterFailureToStartVertx(hookContext, arv.cause());
      return;
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
    if (hooks instanceof VertxApplicationHooksAdapter) {
      VertxApplicationHooksAdapter adapter = (VertxApplicationHooksAdapter) hooks;
      adapter.afterConfigParsed(conf);
    }
    if (conf != null) {
      deploymentOptions.setConfig(conf);
    }
    configureFromSystemProperties(deploymentOptions, DEPLOYMENT_OPTIONS_PROP_PREFIX);
    Supplier<Future<String>> deployer;
    Supplier<Verticle> verticleSupplier = hooks.verticleSupplier();
    if (verticleSupplier == null) {
      String verticleName = computeVerticleName();
      if (verticleName == null) {
        log.error("If the <mainVerticle> parameter is not provided, the 'Main-Verticle' manifest attribute must be provided.");
        return;
      }
      deployer = () -> vertx.deployVerticle(verticleName, deploymentOptions);
      hookContext = hookContext.readyToDeploy(verticleName, deploymentOptions);
    } else {
      deployer = () -> vertx.deployVerticle(verticleSupplier, deploymentOptions);
      hookContext = hookContext.readyToDeploy(null, deploymentOptions);
    }
    hooks.beforeDeployingVerticle(hookContext);

    deploy(deployer);
  }

  private String computeVerticleName() {
    Set<String> attributeNames = new HashSet<>(Arrays.asList("Main-Verticle", "Default-Verticle-Factory"));
    Map<String, String> manifestAttributes;
    try {
      manifestAttributes = getAttributesFromManifest(vertxApplication.getClass(), attributeNames);
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

  private void deploy(Supplier<Future<String>> deployer) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
      Future<String> deployFuture = deployer.get();
      deployFuture.onComplete(ar -> {
        String message = hookContext.deploymentOptions().isWorker() ? "deploying worker verticle" : "deploying verticle";
        if (ar.succeeded()) {
          log.info("Succeeded in " + message);
          hookContext = hookContext.verticleDeployed(ar.result());
          hooks.afterVerticleDeployed(hookContext);
        } else {
          Throwable cause = ar.cause();
          log.error("Failed in " + message, cause);
          hooks.afterFailureToDeployVerticle(hookContext, cause);
        }
      });
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private Future<Vertx> create(VertxBuilder builder) {
    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
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
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private void beforeStoppingVertx(Promise<Void> promise) {
    try {
      hooks.beforeStoppingVertx(hookContext);
      promise.complete();
    } catch (Exception e) {
      promise.fail(e);
    }
  }

  private void shutdownHook() {
    AsyncResult<Void> ar = awaitUninterruptedly(() -> vertx.close(), Duration.ofMinutes(2));
    if (ar == null) {
      log.error("Timed out waiting for Vert.x to be closed");
      hooks.afterFailureToStopVertx(hookContext, null);
    } else if (ar.failed()) {
      log.error("Failure in stopping Vert.x", ar.cause());
      hooks.afterFailureToStopVertx(hookContext, ar.cause());
    } else {
      hooks.afterVertxStopped(hookContext);
    }
  }
}
