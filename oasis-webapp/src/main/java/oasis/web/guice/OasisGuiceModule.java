package oasis.web.guice;

import java.nio.file.Path;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import oasis.model.directory.DirectoryRepository;
import oasis.services.directory.DummyDirectoryRepository;
import oasis.web.NettyOasisServer;
import oasis.web.OasisServer;
import oasis.web.Settings;
import oasis.web.SettingsLoader;

public class OasisGuiceModule extends AbstractModule {

  private final Path configurationPath;

  public OasisGuiceModule(Path configurationPath) {
    this.configurationPath = configurationPath;
  }

  @Override
  protected void configure() {
    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
    bind(OasisServer.class).to(NettyOasisServer.class);
  }

  @Provides
  public Settings providesSettings() {
    return SettingsLoader.load(configurationPath);
  }
}
