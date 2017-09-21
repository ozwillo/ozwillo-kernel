/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.catalog.CatalogModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.jest.JestService;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.soy.SoyGuiceModule;
import oasis.urls.UrlsModule;
import oasis.usecases.DeleteAppInstance.Stats;
import oasis.usecases.ImmutableDeleteAppInstance;

public class DeleteAppInstance extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new DeleteAppInstance().run(args);
  }

  @Option(name = "--instance", aliases = { "--instance-id", "--instance_id" },
      usage = "Instance ID")
  private String instance_id;

  @Option(name = "--creator", aliases = { "--creator-id", "--creator_id" },
      usage = "Creator (instantiator) ID")
  private String creator_id;

  @Option(name = "--org", aliases = { "--organization", "--organization-id", "--organization_id" },
      usage = "Organization ID")
  private String organization_id;

  @Option(name = "--application", aliases = { "--application-id", "--application_id" },
      usage = "Application ID")
  private String application_id;

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject JestService jestService;
  @Inject Provider<oasis.usecases.DeleteAppInstance> usecaseProvider;
  @Inject Provider<AppInstanceRepository> appInstanceRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(DeleteAppInstance.class);
  }

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        UrlsModule.create(config.getConfig("oasis.urls")),
        new SoyGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        HttpClientModule.create(config.getConfig("oasis.http.client")),
        ElasticsearchModule.create(config.getConfig("oasis.elasticsearch")),
        new JestModule(),
        new CatalogModule(),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    jestService.start();
    try {
      if (!Strings.isNullOrEmpty(instance_id)) {
        logger().info("Deleting one instance by ID");
        deleteInstance(instance_id);
      }
      if (!Strings.isNullOrEmpty(creator_id)) {
        logger().info("Deleting instances created by user: {}", creator_id);
        int n = deleteByCreatorId();
        logger().info("  Deleted {} instances.", n);
      }
      if (!Strings.isNullOrEmpty(organization_id)) {
        logger().info("Deleting instances bought for organization: {}", organization_id);
        int n = deleteByOrganizationId();
        logger().info("  Deleted {} instances.", n);
      }
      if (!Strings.isNullOrEmpty(application_id)) {
        logger().info("Deleting instances of application: {}", application_id);
        int n = deleteByApplicationId();
        logger().info("  Deleted {} instances.", n);
      }
    } finally {
      jestService.stop();
      jongoService.stop();
    }
  }

  private void deleteInstance(String instance_id) {
    logger().info("  Deleting instance {}...", instance_id);
    Stats stats = new oasis.usecases.DeleteAppInstance.Stats();
    try {
      if (dryRun) {
        // TODO: reintroduce full dry-run with "dry-run repositories" injected into the use-case class
        // For now, just check whether the instance exists
        stats.appInstanceDeleted = appInstanceRepositoryProvider.get().getAppInstance(instance_id) == null;
      } else {
        ImmutableDeleteAppInstance.Request deleteAppInstanceRequest = ImmutableDeleteAppInstance.Request.builder()
            .instanceId(instance_id)
            .callProvider(false)
            .checkStatus(Optional.absent())
            .checkVersions(Optional.absent())
            .notifyAdmins(false)
            .build();
        usecaseProvider.get().deleteInstance(deleteAppInstanceRequest, stats);
      }
      displayStats(instance_id, stats);
    } catch (Exception e) {
      // display "partial" stats then re-throw
      displayStats(instance_id, stats);
      throw e;
    }
  }

  private void displayStats(String instance_id, Stats stats) {
    if (!stats.appInstanceDeleted) {
      logger().warn("    No instance with ID existed; trying to delete other related data anyway.");
    }

    // TODO: reintroduce full dry-run with "dry-run repositories" injected into the use-case class
    if (!dryRun) {
      logger().info("    Deleted {}credentials for the instance", stats.credentialsDeleted ? "" : "NO ");
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

    logger().info("    Instance {} deleted.", instance_id);
  }

  private int deleteByCreatorId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().findPersonalInstancesByUserId(creator_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }

  private int deleteByOrganizationId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().findByOrganizationId(organization_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }

  private int deleteByApplicationId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().getInstancesForApplication(application_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }
}
