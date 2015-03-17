package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.catalog.CatalogModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.soy.SoyGuiceModule;
import oasis.usecases.DeleteAppInstance;
import oasis.usecases.ImmutableDeleteAppInstance;

public class PurgeDeletedAppInstance extends CommandLineTool {
  private static final Duration APP_INSTANCE_EXPIRATION_DURATION = Duration.standardDays(7);

  public static void main(String[] args) throws Exception {
    new PurgeDeletedAppInstance().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<oasis.usecases.DeleteAppInstance> usecaseProvider;
  @Inject Provider<AppInstanceRepository> appInstanceRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(PurgeDeletedAppInstance.class);
  }

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        new SoyGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        new HttpClientModule(),
        ElasticsearchModule.create(config.getConfig("oasis.elasticsearch")),
        new JestModule(),
        new CatalogModule(),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      logger().info("Deleting stopped instances for {} days", APP_INSTANCE_EXPIRATION_DURATION.getStandardDays());
      int n = deleteStoppedInstances();
      logger().info("  Deleted {} instances.", n);
    } finally {
      jongoService.stop();
    }
  }

  private int deleteStoppedInstances() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().findStoppedBefore(Instant.now().minus(APP_INSTANCE_EXPIRATION_DURATION))) {
      logger().info("  Deleting instance {}...", instance.getId());
      oasis.usecases.DeleteAppInstance.Stats stats = new oasis.usecases.DeleteAppInstance.Stats();
      try {
        if (dryRun) {
          stats.appInstanceDeleted = true;
        } else {
          ImmutableDeleteAppInstance.Request request = ImmutableDeleteAppInstance.Request.builder()
              .instanceId(instance.getId())
              .callProvider(true)
              .checkStatus(AppInstance.InstantiationStatus.STOPPED)
              .checkVersions(Optional.<long[]>absent())
              .notifyAdmins(true)
              .build();
          DeleteAppInstance.Status status = usecaseProvider.get().deleteInstance(request, stats);
          switch (status) {
            case BAD_INSTANCE_STATUS:
              logger().debug("Race condition while trying to delete instance {} caused by a modified status", instance.getId());
              break;
            case PROVIDER_STATUS_ERROR:
            case PROVIDER_CALL_ERROR:
              logger().error("Error while calling provider while trying to purge app-instance {}", instance.getId());
              break;
            case DELETED_INSTANCE:
            case DELETED_LEFTOVERS:
            case NOTHING_TO_DELETE:
            default:
              stats.appInstanceDeleted = true;
          }
        }
      } catch (Exception e) {
        // display "partial" stats then re-throw
        displayStats(stats);
        throw e;
      }
      displayInstanceDeletionStatus(instance.getId(), stats.appInstanceDeleted);
      n++;
    }
    return n;
  }

  private void displayStats(oasis.usecases.DeleteAppInstance.Stats stats) {
    if (!dryRun) {
      logger().info("    Deleted {} credentials for the instance", stats.credentialsDeleted ? "" : "NO ");
      logger().info("    Revoked {} tokens for the instance", stats.tokensRevokedForInstance);
      logger().info("    Deleted {} authorizations from users for the instance", stats.authorizationsDeletedForInstance);
      logger().info("    Revoked {} tokens for the instance scopes", stats.tokensRevokedForScopes);
      logger().info("    Revoked {} authorizations from users to the instance scopes (for other instances)", stats.authorizationsDeletedForScopes);
      logger().info("    Deleted {} scopes", stats.scopesDeleted);
      logger().info("    Deleted {} app_users", stats.appUsersDeleted);
      logger().info("    Deleted {} subscriptions for all services", stats.subscriptionsDeleted);
      logger().info("    Deleted {} services", stats.servicesDeleted);
      logger().info("    Deleted {} eventbus hooks", stats.eventBusHooksDeleted);
    }
  }

  private void displayInstanceDeletionStatus(String instanceId, boolean deleted) {
    if (deleted) {
      logger().info("    Instance {} deleted.", instanceId);
    } else {
      logger().info("    Instance {} not deleted.", instanceId);
    }
  }
}
