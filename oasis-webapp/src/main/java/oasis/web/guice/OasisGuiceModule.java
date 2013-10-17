package oasis.web.guice;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import oasis.model.directory.DirectoryRepository;
import oasis.services.directory.DummyDirectoryRepository;

public class OasisGuiceModule extends AbstractModule {

  @Override
  protected void configure() {
    try {
      Properties props = loadConfiguration();
      Names.bindProperties(binder(), props);
    } catch (IOException e) {
      addError(e);
    }

    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
  }

  private static Properties loadConfiguration() throws IOException {
    Properties defaultProps = new Properties();
    try (Reader reader = Resources.asCharSource(Resources.getResource("defaults.properties"), StandardCharsets.UTF_8).openStream()) {
      defaultProps.load(reader);
    }

    File propFile = new File("etc/oasis.properties");

    if (!propFile.isFile()) {
      return defaultProps;
    }

    Properties props = new Properties(defaultProps);
    try (Reader reader = Files.newReader(propFile, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    return props;
  }
}
