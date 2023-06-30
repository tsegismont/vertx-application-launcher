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

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TerminationRunnable implements Runnable {

  private final Vertx vertx;
  private final Logger log;

  public TerminationRunnable(Vertx vertx, Logger log) {
    this.vertx = Objects.requireNonNull(vertx);
    this.log = Objects.requireNonNull(log);
  }

  @Override
  public void run() {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close().onComplete(ar -> {
      if (!ar.succeeded()) {
        log.error("Failure in stopping Vert.x", ar.cause());
      }
      latch.countDown();
    });
    long remaining = Duration.of(2, MINUTES).toMillis();
    long stop = System.currentTimeMillis() + remaining;
    boolean interrupted = false;
    while (true) {
      try {
        if (remaining <= 0 || !latch.await(remaining, MILLISECONDS)) {
          log.error("Timed out waiting for Vert.x to be closed");
        }
        break;
      } catch (InterruptedException e) {
        interrupted = true;
        remaining = stop - System.currentTimeMillis();
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
