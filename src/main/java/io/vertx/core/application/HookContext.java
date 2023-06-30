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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class HookContext {

  private final VertxOptions vertxOptions;
  private final String mainVerticle;
  private final DeploymentOptions deploymentOptions;
  private final Vertx vertx;
  private final String deploymentId;

  private HookContext(VertxOptions vertxOptions, String mainVerticle, DeploymentOptions deploymentOptions, Vertx vertx, String deploymentId) {
    this.vertxOptions = vertxOptions;
    this.mainVerticle = mainVerticle;
    this.deploymentOptions = deploymentOptions;
    this.vertx = vertx;
    this.deploymentId = deploymentId;
  }

  public VertxOptions vertxOptions() {
    return vertxOptions;
  }

  public String mainVerticle() {
    return mainVerticle;
  }

  public DeploymentOptions deploymentOptions() {
    return deploymentOptions;
  }

  public Vertx vertx() {
    return vertx;
  }

  public String deploymentId() {
    return deploymentId;
  }
}
