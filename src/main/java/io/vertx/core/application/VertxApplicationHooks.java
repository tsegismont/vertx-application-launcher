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

package io.vertx.core.application;

import io.vertx.core.Verticle;

import java.util.function.Supplier;

/**
 *
 */
public interface VertxApplicationHooks {

  VertxApplicationHooks DEFAULT = new VertxApplicationHooks() {
  };

  default void beforeStartingVertx(HookContext context) {
  }

  default void afterVertxStarted(HookContext context) {
  }

  default void afterFailureToStartVertx(HookContext context, Throwable t) {
  }

  default Supplier<Verticle> verticleSupplier() {
    return null;
  }

  default void beforeDeployingVerticle(HookContext context) {
  }

  default void afterVerticleDeployed(HookContext context) {
  }

  default void afterFailureToDeployVerticle(HookContext context, Throwable t) {
    context.vertx().close();
  }

  default void beforeStoppingVertx(HookContext context) {
  }

  default void afterVertxStopped(HookContext context) {
  }

  default void afterFailureToStopVertx(HookContext context, Throwable t) {
  }
}
