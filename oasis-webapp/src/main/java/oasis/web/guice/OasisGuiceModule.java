package oasis.web.guice;

import com.google.inject.AbstractModule;
import oasis.model.directory.DirectoryRepository;
import oasis.services.directory.DummyDirectoryRepository;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
  }
}
