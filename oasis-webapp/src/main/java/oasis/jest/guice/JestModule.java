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
package oasis.jest.guice;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

import io.searchbox.client.JestClient;
import oasis.jest.JestBootstrapper;
import oasis.jest.JestService;
import oasis.jest.applications.v2.JestCatalogEntryRepository;

public class JestModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(JestClient.class).toProvider(JestService.class);

    Multibinder<JestBootstrapper> bootstrappers = newSetBinder(binder(), JestBootstrapper.class);
    bootstrappers.addBinding().to(JestCatalogEntryRepository.class);
  }
}
