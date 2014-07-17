package oasis.tools;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.jongo.MongoCursor;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.eventbus.JongoSubscription;
import oasis.jongo.guice.JongoModule;
import oasis.openidconnect.OpenIdConnectModule;

/** Migrate EventBus subscriptions out of applications and into their own collection. */
public class MigrateEventBusSubscriptions extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateEventBusSubscriptions().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Option(name = "-nc", aliases = "--no-cleanup")
  private boolean noCleanup;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateEventBusSubscriptions.class);
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
      MongoCursor<Application> applications = jongoProvider.get().getCollection("applications")
          .find("{ subscriptions: { $exists: 1 } }")
          .projection("{ id: 1, subscriptions: 1, serviceProvider.id: 1, dataProviders.id: 1 }")
          .as(Application.class);
      for (Application application : applications) {
        String instanceId;
        if (application.serviceProvider != null) {
          instanceId = application.serviceProvider.id;
        } else if (application.dataProviders != null && !application.dataProviders.isEmpty()) {
          instanceId = application.dataProviders.get(0).id;
        } else {
          logger().warn("Not migrating subscriptions: no service provider and data provider");
          continue;
        }
        for (JongoSubscription subscription : application.subscriptions) {
          logger().info("Migrating eventbus subcription {} for application {} and event type {} (webhook: {}), setting instance ID to {}",
              subscription.getId(), application.id, subscription.getEventType(), subscription.getWebHook(), instanceId);
          if (!dryRun) {
            subscription.setInstance_id(instanceId);
            jongoProvider.get().getCollection("subscriptions").insert(subscription);
          }
        }
      }
      if (!dryRun && !noCleanup) {
        jongoProvider.get().getCollection("applications")
            .update("{ subscriptions: { $exists: 1 } }")
            .multi()
            .with("{ $unset: { subscriptions: 1 } }");
      }
    } finally {
      jongoService.stop();
    }
  }

  static class Application {
    @JsonProperty String id;
    @JsonProperty List<JongoSubscription> subscriptions;
    @JsonProperty HasId serviceProvider;
    @JsonProperty List<HasId> dataProviders;
  }

  static class HasId {
    @JsonProperty String id;
  }
}
