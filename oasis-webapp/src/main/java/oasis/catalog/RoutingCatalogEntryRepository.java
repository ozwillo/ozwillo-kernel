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

import javax.inject.Inject;

import oasis.jest.applications.v2.JestCatalogEntryRepository;
import oasis.jongo.applications.v2.JongoCatalogEntryRepository;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.SimpleCatalogEntry;

public class RoutingCatalogEntryRepository implements CatalogEntryRepository {
  private final JongoCatalogEntryRepository jongoCatalogEntryRepository;
  private final JestCatalogEntryRepository jestCatalogEntryRepository;

  @Inject RoutingCatalogEntryRepository(JongoCatalogEntryRepository jongoCatalogEntryRepository,
      JestCatalogEntryRepository jestCatalogEntryRepository) {
    this.jongoCatalogEntryRepository = jongoCatalogEntryRepository;
    this.jestCatalogEntryRepository = jestCatalogEntryRepository;
  }

  @Override
  public Iterable<SimpleCatalogEntry> search(SearchRequest request) {
    if (request.query() != null) {
      return jestCatalogEntryRepository.search(request);
    }
    // If the request doesn't have a query, fallback to the Jongo implementation
    // The problem with the Jest one is that it doesn't support sorting on name properties depending on the user locale
    // if there isn't any scoring (only included by a full text search)
    // FIXME: Make Jest implementation support sorting on requests without query
    return jongoCatalogEntryRepository.search(request);
  }
}
