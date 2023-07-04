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

import io.vertx.core.application.impl.VertxApplicationCommand;
import io.vertx.core.application.impl.VertxApplicationHooksAdapter;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import picocli.CommandLine;

import java.util.Objects;

import static picocli.CommandLine.Help.Ansi;

public class VertxApplication {

  private static final Logger log = LoggerFactory.getLogger(VertxApplication.class);

  private VertxApplicationCommand command;

  public static void main(String[] args) {
    VertxApplication vertxApplication = new VertxApplication();
    int exitCode = vertxApplication.launch(args);
    vertxApplication.processExitCode(exitCode);
  }

  public int launch(String[] args) {
    VertxApplicationHooks hooks;
    if (this instanceof VertxApplicationHooks) {
      hooks = (VertxApplicationHooks) this;
    } else if (this instanceof VertxLifecycleHooks) {
      hooks = new VertxApplicationHooksAdapter((VertxLifecycleHooks) this);
    } else {
      hooks = VertxApplicationHooks.DEFAULT;
    }
    return launch(args, hooks);
  }

  public int launch(String[] args, VertxApplicationHooks hooks) {
    command = new VertxApplicationCommand(this, Objects.requireNonNull(hooks), log);
    CommandLine commandLine = new CommandLine(command)
      .setOptionsCaseInsensitive(true);
    int exitCode = commandLine.execute(args);
    if (exitCode != 0) { // Don't print usage if the verticle has been deployed
      CommandLine.usage(command, System.out, Ansi.ON);
    }
    return exitCode;
  }

  public void processExitCode(int exitCode) {
    if (exitCode != 0) { // Don't exit if the verticle has been deployed
      System.exit(exitCode);
    }
  }
}
