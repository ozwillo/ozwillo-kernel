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

import javax.inject.Inject;
import javax.inject.Provider;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
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
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.soy.SoyGuiceModule;
import oasis.urls.UrlsModule;
import oasis.usecases.DeleteOrganization;
import oasis.usecases.ImmutableDeleteOrganization;

public class PurgeDeletedOrganization extends CommandLineTool {
  private static final Duration ORGANIZATION_EXPIRATION_DURATION = Duration.standardDays(7);

  public static void main(String[] args) throws Exception {
    new PurgeDeletedOrganization().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject JestService jestService;
  @Inject Provider<DeleteOrganization> usecaseProvider;
  @Inject Provider<DirectoryRepository> directoryRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(PurgeDeletedOrganization.class);
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
    try {
      logger().info("Hard-deleting the organizations softly-deleted for {} days", ORGANIZATION_EXPIRATION_DURATION.getStandardDays());
      int n = deleteStoppedOrganizations();
      logger().info("  Deleted {} organizations.", n);
    } finally {
      jestService.stop();
      jongoService.stop();
    }
  }

  private int deleteStoppedOrganizations() {
    Iterable<Organization> organizations = directoryRepositoryProvider.get().findOrganizationsDeletedBefore(Instant.now().minus(ORGANIZATION_EXPIRATION_DURATION));

    if (dryRun) {
      return Iterables.size(organizations);
    }

    int deletedOrganizations = 0;
    for (Organization organization : organizations) {
      ImmutableDeleteOrganization.Request deleteOrganizationRequest = ImmutableDeleteOrganization.Request.builder()
          .organizationId(organization.getId())
          .organizationName(organization.getName())
          .checkStatus(Organization.Status.DELETED)
          .notifyAdmins(true)
          .build();
      DeleteOrganization.ResponseStatus deleteOrganizationResponseStatus = usecaseProvider.get().deleteOrganization(deleteOrganizationRequest);
      switch (deleteOrganizationResponseStatus) {
        case SUCCESS:
          // fall through
        case APP_INSTANCE_PROVIDER_ERROR:
          // Even if the app-instance might not be deleted by the provider, the organization stays deleted
          deletedOrganizations++;
          break;
        case BAD_ORGANIZATION_STATUS:
          break;
        default:
          // noop
      }
    }
    return deletedOrganizations;
  }
}
