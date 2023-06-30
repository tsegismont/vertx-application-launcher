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
