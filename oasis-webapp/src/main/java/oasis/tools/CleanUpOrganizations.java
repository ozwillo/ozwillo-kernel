/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Client;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Joiner;
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
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.soy.SoyGuiceModule;
import oasis.urls.UrlsModule;
import oasis.usecases.*;
import oasis.usecases.DeleteAppInstance;

public class CleanUpOrganizations extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new CleanUpOrganizations().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject JestService jestService;
  @Inject Client httpClient;
  @Inject Provider<ApplicationRepository> applicationRepositoryProvider;
  @Inject Provider<OrganizationMembershipRepository> organizationMembershipRepositoryProvider;
  @Inject Provider<AppInstanceRepository> appInstanceRepositoryProvider;
  @Inject Provider<oasis.usecases.DeleteAppInstance> deleteAppInstanceProvider;
  @Inject Provider<DeleteOrganization> deleteOrganizationProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(CleanUpOrganizations.class);
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
    jestService.start();

    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
      System.out.println("Application\tInstance\tOrganization\tStatus");
      for (String orgId = in.readLine(); orgId != null; orgId = in.readLine()) {
        if (Strings.isNullOrEmpty(orgId)) {
          continue;
        }
        deleteOrganization(orgId, System.out);
      }
    } finally {
      httpClient.close();
      jestService.stop();
      jongoService.stop();
    }
  }

  private void deleteOrganization(String organizationId, PrintStream out) {
    if (applicationRepositoryProvider.get().getCountByProvider(organizationId) > 0) {
      logger().info("Organization {} provides applications; skipping.");
      return;
    }
    logger().info("Deleting organization {}", organizationId);

    // Delete all members
    if (!dryRun) {
      organizationMembershipRepositoryProvider.get().deleteMembershipsInOrganization(organizationId);
    }

    // Delete app-instances
    for (AppInstance instance : appInstanceRepositoryProvider.get().findByOrganizationId(organizationId)) {
      deleteInstance(instance, out);
    }

    if (!dryRun) {
      // Now we can delete the organization
      deleteOrganizationProvider.get().deleteOrganization(ImmutableDeleteOrganization.Request.builder()
          .organizationId(organizationId)
          .organizationName(Optional.<String>absent())
          .checkStatus(Optional.<Organization.Status>absent())
          .notifyAdmins(false)
          .build());
    }
    logger().info("  Organization {} deleted", organizationId);
  }

  private void deleteInstance(AppInstance instance, PrintStream out) {
    logger().info("  Deleting app-instance {}", instance.getId());

    final boolean cancellable;
    switch (instance.getStatus()) {
      case RUNNING:
      case STOPPED:
        cancellable = !Strings.isNullOrEmpty(instance.getDestruction_uri());
        break;
      case PENDING:
        Application application = applicationRepositoryProvider.get().getApplication(instance.getApplication_id());
        cancellable = application != null && !Strings.isNullOrEmpty(application.getCancellation_uri());
        break;
      default:
        throw new AssertionError();
    }

    final DeleteAppInstance.Status status;
    if (dryRun) {
      status = DeleteAppInstance.Status.DELETED_INSTANCE;
    } else {
      ImmutableDeleteAppInstance.Request request = ImmutableDeleteAppInstance.Request.builder()
          .instanceId(instance.getId())
          .callProvider(cancellable)
          .checkStatus(instance.getStatus())
          .checkVersions(Optional.<long[]>absent())
          .notifyAdmins(false)
          .build();
      status = deleteAppInstanceProvider.get().deleteInstance(request, new DeleteAppInstance.Stats());
    }

    switch (status) {
      case DELETED_INSTANCE:
        logger().info("    App-instance {} deleted.", instance.getId());
        if (!cancellable) {
          log(instance, out);
        }
        break;
      case PROVIDER_CALL_ERROR:
      case PROVIDER_STATUS_ERROR:
        log(instance, out);
        break;
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
        logger().info("  App-instance {} deleted while processing organization {}; deleted (possible) leftovers.",
            instance.getId(), instance.getProvider_id());
        break;
      case BAD_INSTANCE_STATUS:
        // Instance could have been provisionned since we retrieved it; retry.
        logger().info("App-instance {} changed status (was {}) while processing organization {}; retrying.",
            instance.getId(), instance.getStatus(), instance.getProvider_id());
        deleteInstance(appInstanceRepositoryProvider.get().getAppInstance(instance.getId()), out);
        break;
      case BAD_INSTANCE_VERSION:
      default:
        throw new AssertionError();
    }
  }

  private void log(AppInstance instance, PrintStream out) {
    out.println(instance.getApplication_id() + "\t" + instance.getId() + "\t" + instance.getProvider_id() + "\t" + instance.getStatus());
  }
}
