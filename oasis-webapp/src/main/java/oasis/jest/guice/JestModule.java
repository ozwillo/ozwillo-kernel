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
