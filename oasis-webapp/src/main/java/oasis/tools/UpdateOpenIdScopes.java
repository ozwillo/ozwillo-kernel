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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.ScopeRepository;

public class UpdateOpenIdScopes extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new UpdateOpenIdScopes().run(args);
  }

  @Inject JongoService jongoService;
  @Inject Provider<ScopeRepository> scopeRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(UpdateOpenIdScopes.class);
  }

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo")),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      Bootstrap.createOrUpdateOpenIdConnectScopes(scopeRepositoryProvider.get());
    } finally {
      jongoService.stop();
    }
  }
}
