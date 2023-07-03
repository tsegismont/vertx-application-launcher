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

public class VertxApplication {

  private static final Logger log = LoggerFactory.getLogger(VertxApplication.class);

  public static void main(String[] args) {
    VertxApplication vertxApplication = new VertxApplication();
    vertxApplication.launch(args);
  }

  protected void launch(String[] args) {
    VertxApplicationHooks hooks;
    if (this instanceof VertxApplicationHooks) {
      hooks = (VertxApplicationHooks) this;
    } else if (this instanceof VertxLifecycleHooks) {
      hooks = new VertxApplicationHooksAdapter((VertxLifecycleHooks) this);
    } else {
      hooks = VertxApplicationHooks.DEFAULT;
    }
    launch(args, hooks);
  }

  protected void launch(String[] args, VertxApplicationHooks hooks) {
    VertxApplicationCommand command = new VertxApplicationCommand(this, Objects.requireNonNull(hooks), log);
    CommandLine commandLine = new CommandLine(command)
      .setOptionsCaseInsensitive(true);
    commandLine.execute(args);
  }
}
