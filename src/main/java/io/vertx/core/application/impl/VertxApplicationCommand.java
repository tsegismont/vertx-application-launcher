package io.vertx.core.application.impl;

import io.vertx.core.Vertx;
import io.vertx.core.application.VertxApplicationHooks;
import io.vertx.core.impl.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static picocli.CommandLine.Parameters.NULL_VALUE;

@Command(name = "VertxApplication", description = "Runs a Vert.x application.")
public class VertxApplicationCommand implements Runnable {

  @Option(
    names = {"--options", "--vertx-options"},
    description = {
      "Specifies the Vert.x options.",
      "It should reference either a JSON file which represents the options OR be a JSON string."
    },
    defaultValue = Option.NULL_VALUE
  )
  private String vertxOptions;

  @Option(
    names = {"-c", "--clustered"},
    description = {
      "If specified, then the Vert.x instance will form a cluster with any other Vert.x instances on the network."
    },
    arity = "0"
  )
  private Boolean clustered;
  @Option(
    names = {"--cluster-port"},
    description = {
      "Port to use for cluster communication.",
      "By default, a spare random port is chosen."
    }
  )
  private Integer clusterPort;
  @Option(
    names = {"--cluster-host"},
    description = {
      "Host to bind to for cluster communication.",
      "If this is not specified, Vert.x will attempt to choose one from the available interfaces."
    }
  )
  private String clusterHost;
  @Option(
    names = {"--cluster-public-port"},
    description = {
      "Public port to use for cluster communication.",
      "By default, Vert.x uses the same as the cluster port."
    }
  )
  private Integer clusterPublicPort;
  @Option(
    names = {"--cluster-public-host"},
    description = {
      "Public host to bind to for cluster communication.",
      "By default, Vert.x uses the same as the cluster host."
    }
  )
  private String clusterPublicHost;

  @Option(
    names = {"--deployment-options"},
    description = {
      "Specifies the main verticle deployment options."
    }
  )
  private String deploymentOptions;

  @Option(
    names = {"-w", "--worker"},
    description = {
      "If specified, then the main verticle is deployed as a worker verticle.",
      "Takes precedences over the value defined in deployment options.",
    },
    arity = "0"
  )
  private Boolean worker;
  @Option(
    names = {"--instances"},
    description = {
      "Specifies how many instances of the verticle will be deployed.",
      "Takes precedences over the value defined in deployment options."
    }
  )
  private Integer instances;

  @Option(
    names = {"--conf"},
    description = {
      "Specifies configuration that should be provided to the verticle.",
      "It should reference either a JSON file which represents the options OR be a JSON string."
    }
  )
  private String config;

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = {
      "Display a help message."
    },
    arity = "0")
  private boolean helpRequested;

  @Parameters(
    index = "0",
    description = {
      "The main verticle fully qualified class name."
    },
    defaultValue = NULL_VALUE
  )
  private String mainVerticle;

  private final Logger log;
  private final VertxApplicationHooks hooks;

  private volatile Vertx vertx;

  public VertxApplicationCommand(Logger log, VertxApplicationHooks hooks) {
    this.log = log;
    this.hooks = hooks;
  }

  @Override
  public void run() {
  }
}
