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

import io.vertx.core.application.HookContext;
import io.vertx.core.application.VertxApplicationHooks;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.json.JsonObject;

public class VertxApplicationHooksAdapter implements VertxApplicationHooks {

  private final VertxLifecycleHooks adapted;

  public VertxApplicationHooksAdapter(VertxLifecycleHooks adapted) {
    this.adapted = adapted;
  }

  public void afterConfigParsed(JsonObject config) {
    adapted.afterConfigParsed(config);
  }

  @Override
  public void beforeStartingVertx(HookContext context) {
    adapted.beforeStartingVertx(context.vertxOptions());
  }

  @Override
  public void afterVertxStarted(HookContext context) {
    adapted.afterStartingVertx(context.vertx());
  }

  @Override
  public void beforeDeployingVerticle(HookContext context) {
    adapted.beforeDeployingVerticle(context.deploymentOptions());
  }

  @Override
  public void afterFailureToDeployVerticle(HookContext context, Throwable t) {
    adapted.handleDeployFailed(context.vertx(), context.mainVerticle(), context.deploymentOptions(), t);
  }

  @Override
  public void beforeStoppingVertx(HookContext context) {
    adapted.beforeStoppingVertx(context.vertx());
  }

  @Override
  public void afterVertxStopped(HookContext context) {
    adapted.afterStoppingVertx();
  }

  @Override
  public void afterFailureToStopVertx(HookContext context, Throwable t) {
    adapted.afterStoppingVertx();
  }
}
