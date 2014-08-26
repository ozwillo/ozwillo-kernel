package oasis.tools;

import java.util.Collections;

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
import oasis.model.applications.v2.CatalogEntry;
import oasis.openidconnect.OpenIdConnectModule;

public class MigrateTargetAudience extends CommandLineTool {

  public static void main(String[] args) throws Throwable {
    new MigrateTargetAudience().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateTargetAudience.class);
  }

  public void run(String[] args) throws Throwable {
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
      for (String collectionName : new String[] { "applications", "services" }) {
        for (CatalogEntry.TargetAudience targetAudience : CatalogEntry.TargetAudience.values()) {
          long n;
          if (dryRun) {
            n = jongoProvider.get().getCollection(collectionName)
                .count("{ target_audience: # }", targetAudience);
          } else {
            n = jongoProvider.get().getCollection(collectionName)
                .update("{ target_audience: # }", targetAudience)
                .multi()
                .with("{ $set: { target_audience: # } }", Collections.singletonList(targetAudience))
                .getN();
          }
          logger().info("Updated {} {} with target_audience={}", n, collectionName, targetAudience);
        }
      }
    } finally {
      jongoService.stop();
    }
  }
}
