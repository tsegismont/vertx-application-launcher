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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static io.vertx.core.http.impl.HttpClientConnection.log;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

public class Utils {

  public static JsonObject readJsonFileOrString(String optionName, String jsonFileOrString) {
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

  public static <T> AsyncResult<T> await(Supplier<Future<T>> supplier, Duration duration) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Future<T> future = supplier.get().andThen(v -> latch.countDown());
    if (latch.await(duration.toMillis(), MILLISECONDS)) {
      return future;
    }
    return null;
  }

  public static <T> AsyncResult<T> awaitUninterruptedly(Supplier<Future<T>> supplier, Duration duration) {
    CountDownLatch latch = new CountDownLatch(1);
    Future<T> future = supplier.get().andThen(v -> latch.countDown());
    long remaining = duration.toMillis();
    long stop = System.currentTimeMillis() + remaining;
    boolean interrupted = false;
    while (true) {
      try {
        if (remaining >= 0) {
          if (latch.await(remaining, MILLISECONDS)) {
            return future;
          }
        }
        return null;
      } catch (InterruptedException e) {
        interrupted = true;
        remaining = stop - System.currentTimeMillis();
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  public static Map<String, String> getAttributesFromManifest(Class<?> mainClass, Set<String> attributeNames) throws IOException {
    Enumeration<URL> resources = Utils.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
    while (resources.hasMoreElements()) {
      try (InputStream stream = resources.nextElement().openStream()) {
        Manifest manifest = new Manifest(stream);
        Attributes attributes = manifest.getMainAttributes();
        String mainClassName = attributes.getValue("Main-Class");
        if (mainClass.getName().equals(mainClassName)) {
          return attributeNames.stream().collect(collectingAndThen(toMap(Function.identity(), attributes::getValue), Collections::unmodifiableMap));
        }
      }
    }
    return Collections.emptyMap();
  }

  private Utils() {
  }
}
