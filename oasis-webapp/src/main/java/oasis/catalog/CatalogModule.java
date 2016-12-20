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
package oasis.catalog;

import com.google.inject.AbstractModule;

import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.ServiceRepository;

public class CatalogModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ServiceRepository.class).to(IndexingServiceRepository.class);
    bind(CatalogEntryRepository.class).to(RoutingCatalogEntryRepository.class);
  }
}
