package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.openidconnect.OpenIdConnectModule;

public class MigrateTokens extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateTokens().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateTokens.class);
  }

  private void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo")),
        // TODO: refactor to use a single subtree of the config
        OpenIdConnectModule.create(config.withOnlyPath("oasis.openid-connect")
            .withFallback(config.withOnlyPath("oasis.oauth"))
            .withFallback(config.withOnlyPath("oasis.session"))
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      logger().info("Deleting all tokens");
      if (!dryRun) {
        jongoProvider.get().getCollection("account")
            .update("{ tokens: { $exists: 1 } }")
            .multi()
            .with("{ $unset: { tokens: 1 } }");
      }
    } finally {
      jongoService.stop();
    }
  }
}
