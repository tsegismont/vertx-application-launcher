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

import io.vertx.core.AsyncResult;

/**
 *
 */
public interface VertxApplicationHooks {

  VertxApplicationHooks DEFAULT = new VertxApplicationHooks() {
  };

  default void beforeStartingVertx(HookContext context) {
  }

  default void afterStartingVertx(HookContext context, AsyncResult<Void> res) {
  }

  default void beforeDeployingVerticle(HookContext context) {
  }

  default void afterDeployingVerticle(HookContext context, AsyncResult<Void> res) {
    if (res.failed()) {
      context.vertx().close();
    }
  }

  default void beforeStoppingVertx(HookContext context) {
  }

  default void afterStoppingVertx(HookContext context, AsyncResult<Void> res) {
  }
}
