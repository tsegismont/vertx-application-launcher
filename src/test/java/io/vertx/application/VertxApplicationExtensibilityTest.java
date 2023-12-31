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

import io.vertx.core.Verticle;
import io.vertx.core.application.HookContext;
import io.vertx.core.application.VertxApplication;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.launcher.commands.HttpTestVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.function.Supplier;

import static io.vertx.application.VertxApplicationTest.*;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class VertxApplicationExtensibilityTest {

  private MyHooks hooks;

  @AfterEach
  public void tearDown() {
    if (hooks != null && hooks.vertx != null) {
      hooks.vertx.close();
    }
    FakeClusterManager.reset();
  }

  @Test
  public void testExtendingMainVerticle() {
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks() {
      @Override
      public Supplier<Verticle> verticleSupplier() {
        return () -> new HttpTestVerticle();
      }
    };
    myVertxApplication.launch(new String[0], hooks);
    assertServerStarted();
  }

  @Test
  public void testThatCustomLauncherCanUpdateConfigurationWhenNoneArePassed() throws IOException {
    long time = System.nanoTime();
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks() {
      @Override
      public Supplier<Verticle> verticleSupplier() {
        return () -> new HttpTestVerticle();
      }

      @Override
      public void beforeDeployingVerticle(HookContext context) {
        context.deploymentOptions().setConfig(new JsonObject().put("time", time));
      }
    };
    myVertxApplication.launch(new String[0], hooks);
    assertServerStarted();
    assertEquals(time, getContent().getJsonObject("conf").getLong("time"));
  }

  @Test
  public void testThatCustomLauncherCanUpdateConfiguration() throws IOException {
    long time = System.nanoTime();
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks() {
      @Override
      public Supplier<Verticle> verticleSupplier() {
        return () -> new HttpTestVerticle();
      }

      @Override
      public void beforeDeployingVerticle(HookContext context) {
        context.deploymentOptions().getConfig().put("time", time);
      }
    };
    myVertxApplication.launch(new String[]{"-conf={\"time\":345667}"}, hooks);
    assertServerStarted();
    assertEquals(time, getContent().getJsonObject("conf").getLong("time"));
  }

  @Test
  public void testThatCustomLauncherCanCustomizeMetricsOption() throws Exception {
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks() {
      @Override
      public Supplier<Verticle> verticleSupplier() {
        return () -> new HttpTestVerticle();
      }

      @Override
      public void beforeStartingVertx(HookContext context) {
        context.vertxOptions().getMetricsOptions()
          .setEnabled(true)
          .setFactory(options -> DummyVertxMetrics.INSTANCE);
      }
    };
    myVertxApplication.launch(new String[0], hooks);
    assertServerStarted();
    assertEquals(TRUE, getContent().getBoolean("metrics"));
  }

  @Test
  public void testThatCustomLauncherCanCustomizeClusterManager() throws Exception {
    FakeClusterManager clusterManager = new FakeClusterManager();
    VertxApplication myVertxApplication = new VertxApplication();
    hooks = new MyHooks() {
      @Override
      public Supplier<Verticle> verticleSupplier() {
        return () -> new HttpTestVerticle();
      }

      @Override
      public void beforeStartingVertx(HookContext context) {
        context.vertxOptions().setClusterManager(clusterManager);
      }
    };
    myVertxApplication.launch(new String[]{"-cluster"}, hooks);
    assertServerStarted();
    assertEquals(TRUE, getContent().getBoolean("clustered"));
    assertSame(clusterManager, ((VertxInternal) hooks.vertx).getClusterManager());
  }
}
