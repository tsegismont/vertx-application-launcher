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

  private final VertxOptions vertxOptions;
  private final Vertx vertx;
  private final String mainVerticle;
  private final DeploymentOptions deploymentOptions;
  private final String deploymentId;

  private HookContextImpl(VertxOptions vertxOptions, Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, String deploymentId) {
    this.vertxOptions = vertxOptions;
    this.mainVerticle = mainVerticle;
    this.deploymentOptions = deploymentOptions;
    this.vertx = vertx;
    this.deploymentId = deploymentId;
  }

  public static HookContextImpl create(VertxOptions vertxOptions) {
    return new HookContextImpl(vertxOptions, null, null, null, null);
  }

  @Override
  public VertxOptions vertxOptions() {
    return vertxOptions;
  }

  public HookContextImpl vertxStarted(Vertx vertx) {
    return new HookContextImpl(vertxOptions, vertx, null, null, null);
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  public HookContextImpl readyToDeploy(String mainVerticle, DeploymentOptions deploymentOptions) {
    return new HookContextImpl(vertxOptions, vertx, mainVerticle, deploymentOptions, null);
  }

  @Override
  public String mainVerticle() {
    return mainVerticle;
  }

  @Override
  public DeploymentOptions deploymentOptions() {
    return deploymentOptions;
  }

  public HookContextImpl verticleDeployed(String deploymentId) {
    return new HookContextImpl(vertxOptions, vertx, mainVerticle, deploymentOptions, deploymentId);
  }

  @Override
  public String deploymentId() {
    return deploymentId;
  }
}
