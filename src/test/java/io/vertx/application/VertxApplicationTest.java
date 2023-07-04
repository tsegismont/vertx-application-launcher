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

package io.vertx.application;

import io.vertx.core.Vertx;
import io.vertx.core.application.HookContext;
import io.vertx.core.application.VertxApplication;
import io.vertx.core.application.VertxApplicationHooks;
import io.vertx.core.impl.launcher.commands.HttpTestVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.Callable;

import static io.vertx.core.impl.launcher.commands.ExecUtils.VERTX_DEPLOYMENT_EXIT_CODE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxApplicationTest {

  private MyHooks hooks;
  private Path manifest;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @AfterEach
  public void tearDown() throws IOException {
    if (manifest != null) {
      Files.deleteIfExists(manifest);
    }
    if (hooks != null && hooks.vertx != null) {
      hooks.vertx.close();
    }
    FakeClusterManager.reset();
  }

  private void setManifest(String name) throws Exception {
    URI resource = getClass().getClassLoader().getResource(name).toURI();
    assertEquals("file", resource.getScheme());
    Path source = Paths.get(resource);
    manifest = source.getParent().resolve("MANIFEST.MF");
    Files.copy(source, manifest, REPLACE_EXISTING, COPY_ATTRIBUTES);
  }

  @Test
  public void testDeploymentOfJavaVerticle() {
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks();
    myVertxApplication.launch(new String[]{HttpTestVerticle.class.getName()}, hooks);
    await("Server not started")
      .atMost(Duration.ofSeconds(10))
      .until(() -> getHttpCode(), equalTo(200));
  }

  @Test
  public void testDeploymentOfJavaVerticleWithCluster() throws IOException {
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks();
    myVertxApplication.launch(new String[]{HttpTestVerticle.class.getName(), "-cluster"}, hooks);
    await("Server not started")
      .atMost(Duration.ofSeconds(10))
      .until(() -> getHttpCode(), equalTo(200));
    assertEquals(TRUE, getContent().getBoolean("clustered"));
  }

  @Test
  public void testFatJarWithoutMainVerticle() throws Exception {
    setManifest("META-INF/MANIFEST-No-Main-Verticle.MF");
    Integer exitCode = captureOutput(() -> {
      VertxApplication myVertxApplication = new VertxApplication();
      hooks = new MyHooks();
      return myVertxApplication.launch(new String[0], hooks);
    });
    assertEquals(VERTX_DEPLOYMENT_EXIT_CODE, exitCode);
    assertTrue(out.toString().contains("Usage:"));
  }

  @Test
  public void testFatJarWithMissingMainVerticle() throws Exception {
    setManifest("META-INF/MANIFEST-Missing-Main-Verticle.MF");
    Integer exitCode = captureOutput(() -> {
      VertxApplication myVertxApplication = new VertxApplication();
      hooks = new MyHooks();
      return myVertxApplication.launch(new String[0], hooks);
    });
    assertEquals(VERTX_DEPLOYMENT_EXIT_CODE, exitCode);
    assertTrue(out.toString().contains("Usage:"));
    assertTrue(err.toString().contains("ClassNotFoundException"));
  }

  @Test
  public void testFatJarWithHTTPVerticle() throws Exception {
    setManifest("META-INF/MANIFEST-Http-Verticle.MF");
    captureOutput(() -> {
      VertxApplication myVertxApplication = new VertxApplication();
      hooks = new MyHooks();
      return myVertxApplication.launch(new String[0], hooks);
    });
    await("Server not started")
      .atMost(Duration.ofSeconds(10))
      .until(() -> getHttpCode(), equalTo(200));
    assertEquals(FALSE, getContent().getBoolean("clustered"));
  }

  @Test
  public void testFatJarWithHTTPVerticleWithCluster() throws Exception {
    setManifest("META-INF/MANIFEST-Http-Verticle.MF");
    captureOutput(() -> {
      VertxApplication myVertxApplication = new VertxApplication();
      hooks = new MyHooks();
      return myVertxApplication.launch(new String[]{"-cluster"}, hooks);
    });
    await("Server not started")
      .atMost(Duration.ofSeconds(10))
      .until(() -> getHttpCode(), equalTo(200));
    assertEquals(TRUE, getContent().getBoolean("clustered"));
  }

  public static class MyHooks implements VertxApplicationHooks {

    volatile Vertx vertx;

    @Override
    public void afterVertxStarted(HookContext context) {
      vertx = context.vertx();
    }
  }

  private Integer captureOutput(Callable<Integer> callable) throws Exception {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    try {
      out = new ByteArrayOutputStream();
      PrintStream psOut = new PrintStream(out);
      System.setOut(psOut);

      err = new ByteArrayOutputStream();
      PrintStream psErr = new PrintStream(err);
      System.setErr(psErr);

      Integer exitCode = callable.call();

      psOut.flush();
      psErr.flush();

      return exitCode;

    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  static int getHttpCode() throws IOException {
    return ((HttpURLConnection) new URL("http://localhost:8080")
      .openConnection()).getResponseCode();
  }

  static JsonObject getContent() throws IOException {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    StringBuilder builder = new StringBuilder();
    try (BufferedReader buff = new BufferedReader(new InputStreamReader((InputStream) conn.getContent()))) {
      while (true) {
        String line = buff.readLine();
        if (line == null) {
          break;
        }
        builder.append(line).append("\n");
      }
    }
    return new JsonObject(builder.toString());
  }
}
