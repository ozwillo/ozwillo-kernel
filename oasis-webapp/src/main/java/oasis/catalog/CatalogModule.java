package oasis.catalog;

import com.google.inject.AbstractModule;

import oasis.model.applications.v2.ServiceRepository;

public class CatalogModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ServiceRepository.class).to(IndexingServiceRepository.class);
  }
}
