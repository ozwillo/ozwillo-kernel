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

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.catalog.CatalogModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.jest.JestService;
import oasis.jest.applications.v2.JestCatalogEntryRepository;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.applications.v2.JongoApplicationRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.CatalogEntry;
import oasis.soy.SoyGuiceModule;
import oasis.urls.UrlsModule;

public class InitializeCatalogIndex extends CommandLineTool {
  public static void main(String[] args) throws Exception {
    new InitializeCatalogIndex().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JestService jestService;
  @Inject JongoService jongoService;
  @Inject Provider<JestCatalogEntryRepository> jestCatalogEntryRepositoryProvider;
  @Inject Provider<JongoApplicationRepository> jongoApplicationRepositoryProvider;
  @Inject Provider<JongoServiceRepository> jongoServiceRepositoryProvider;

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
        new CatalogModule(),
        HttpClientModule.create(config.getConfig("oasis.http.client")),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    jestService.start();

    try {
      Iterable<? extends CatalogEntry> applications = jongoApplicationRepositoryProvider.get().getAllInCatalog();
      Iterable<? extends CatalogEntry> services = jongoServiceRepositoryProvider.get().getAllInCatalog();

      for (final CatalogEntry catalogEntry : Iterables.concat(applications, services)) {
        logger().info("Indexing {} {} ...", catalogEntry.getType(), catalogEntry.getId());

        if (dryRun) {
          logger().info("{} {} successfully indexed", catalogEntry.getType(), catalogEntry.getId());
          continue;
        }

        boolean success;
        try {
          jestCatalogEntryRepositoryProvider.get()
              .asyncIndex(catalogEntry)
              .get();
          success = true;
        } catch (Exception e) {
          logger().error("Error when indexing {} {}", catalogEntry.getType(), catalogEntry.getId(), e);
          success = false;
        }
        if (success) {
          logger().info("{} {} successfully indexed", catalogEntry.getType(), catalogEntry.getId());
        }
      }
    } finally {
      jongoService.stop();
      jestService.stop();
    }
  }

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(InitializeCatalogIndex.class);
  }
}
