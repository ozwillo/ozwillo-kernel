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
    if (request.query().isPresent()) {
      return jestCatalogEntryRepository.search(request);
    }
    // If the request doesn't have a query, fallback to the Jongo implementation
    // The problem with the Jest one is that it doesn't support sorting on name properties depending on the user locale
    // if there isn't any scoring (only included by a full text search)
    // FIXME: Make Jest implementation support sorting on requests without query
    return jongoCatalogEntryRepository.search(request);
  }
}
