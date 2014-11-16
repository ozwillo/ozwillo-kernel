package oasis.elasticsearch;

import java.net.URI;

import org.immutables.value.Value;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

@Value.Nested
public class ElasticsearchModule extends AbstractModule {

  public static final String ELASTICSEARCH = "elasticsearch";

  @Value.Immutable
  public interface Settings {
    URI url();
  }

  public static ElasticsearchModule create(Config config) {
    return new ElasticsearchModule(ImmutableElasticsearchModule.Settings.builder()
        .url(URI.create(config.getString("url")))
        .build());
  }

  private final Settings settings;

  public ElasticsearchModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }
}
