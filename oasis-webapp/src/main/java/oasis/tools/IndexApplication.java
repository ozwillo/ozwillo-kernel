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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.jest.JestService;
import oasis.jest.applications.v2.JestCatalogEntryRepository;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntry;
import oasis.soy.SoyGuiceModule;
import oasis.urls.UrlsModule;

public class IndexApplication extends CommandLineTool {
  public static void main(String[] args) throws Exception {
    new IndexApplication().run(args);
  }

  @Argument(required = true)
  private String applicationId;

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject JestService jestService;
  @Inject Provider<ApplicationRepository> applicationRepositoryProvider;
  @Inject Provider<JestCatalogEntryRepository> jestCatalogEntryRepositoryProvider;

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        UrlsModule.create(config.getConfig("oasis.urls")),
        new SoyGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        ElasticsearchModule.create(config.getConfig("oasis.elasticsearch")),
        new JestModule(),
        HttpClientModule.create(config.getConfig("oasis.http.client")),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    jestService.start();

    try {
      Application application = applicationRepositoryProvider.get().getApplication(applicationId);

      if (application != null && application.isVisible()) {
        logger().info("Indexing application {} ...", applicationId);

        boolean success;
        try {
          if (!dryRun) {
            jestCatalogEntryRepositoryProvider.get().asyncIndex(application).toCompletableFuture().get();
          }
          success = true;
        } catch (Exception e) {
          logger().error("Error when indexing application {}", applicationId);
          success = false;
        }
        if (success) {
          logger().info("Application {} successfully indexed", applicationId);
        }
      } else {
        logger().info("Removing application {} from the index ...", applicationId);

        boolean success;
        try {
          if (!dryRun) {
            jestCatalogEntryRepositoryProvider.get()
                .asyncDelete(applicationId, CatalogEntry.EntryType.APPLICATION)
                .toCompletableFuture()
                .get();
          }
          success = true;
        } catch (Exception e) {
          logger().error("Error when removing application {} from the index", applicationId);
          success = false;
        }
        if (success) {
          logger().info("Application {} successfully removed from the index", applicationId);
        }
      }
    } finally {
      jongoService.stop();
      jestService.stop();
    }
  }

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(IndexApplication.class);
  }
}
