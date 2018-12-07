/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.AppInstance;
import oasis.model.bootstrap.ClientIds;

/**
 * Migrate the Portal application's ID to be {@link oasis.model.bootstrap.ClientIds#PORTAL}.
 */
public class MigratePortalAppId extends CommandLineTool {

  public static void main(String[] args) throws Throwable {
    new MigratePortalAppId().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigratePortalAppId.class);
  }

  public void run(String[] args) throws Throwable {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo")),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      final String oldAppId = jongoService.get().getCollection("app_instances")
          .findOne("{ id: # }", ClientIds.PORTAL)
          .projection("{ application_id: 1 }")
          .as(AppInstance.class)
          .getApplication_id();
      logger().info("Reassigning ID of application {} to {}", oldAppId, ClientIds.PORTAL);

      long numInstances;
      if (dryRun) {
        numInstances = jongoService.get().getCollection("app_instances")
            .count("{ application_id: # }", oldAppId);
      } else {
        jongoService.get().getCollection("applications")
            .update("{ id: # }", oldAppId)
            .with("{ $set: { id: # } }", ClientIds.PORTAL);
        numInstances = jongoService.get().getCollection("app_instances")
            .update("{ application_id: # }", oldAppId)
            .multi()
            .with("{ $set: { application_id: # } }", ClientIds.PORTAL)
            .getN();
      }
      logger().info("Reassigned {} app instances to their new application_id", numInstances);
    } finally {
      jongoService.stop();
    }
  }
}
