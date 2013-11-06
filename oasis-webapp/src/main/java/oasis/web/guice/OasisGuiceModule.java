package oasis.web.guice;

import com.google.inject.AbstractModule;

import oasis.model.directory.DirectoryRepository;
import oasis.services.directory.DummyDirectoryRepository;
import oasis.web.Settings;

public class OasisGuiceModule extends AbstractModule {

  private Settings settings;

  public OasisGuiceModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
  }
}
