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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.application.HookContext;

public class HookContextImpl implements HookContext {

  private VertxOptions vertxOptions;
  private Vertx vertx;
  private String mainVerticle;
  private DeploymentOptions deploymentOptions;
  private String deploymentId;

  public HookContextImpl setVertxOptions(VertxOptions vertxOptions) {
    this.vertxOptions = vertxOptions;
    return this;
  }

  @Override
  public VertxOptions vertxOptions() {
    return vertxOptions;
  }

  public HookContextImpl setVertx(Vertx vertx) {
    this.vertx = vertx;
    return this;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  public HookContextImpl setMainVerticle(String mainVerticle) {
    this.mainVerticle = mainVerticle;
    return this;
  }

  public HookContextImpl setDeploymentOptions(DeploymentOptions deploymentOptions) {
    this.deploymentOptions = deploymentOptions;
    return this;
  }

  @Override
  public String mainVerticle() {
    return mainVerticle;
  }

  @Override
  public DeploymentOptions deploymentOptions() {
    return deploymentOptions;
  }

  public HookContextImpl setDeploymentId(String deploymentId) {
    this.deploymentId = deploymentId;
    return this;
  }

  @Override
  public String deploymentId() {
    return deploymentId;
  }
}
