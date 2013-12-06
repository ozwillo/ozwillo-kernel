package oasis.web.kibana;

import java.net.URI;

import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

public class KibanaModule extends AbstractModule {

  public static final String ELASTICSEARCH = "elasticsearch";

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setElasticsearchUrl(URI.create(config.getString("oasis.kibana.elasticsearch.url")))
          .build();
    }

    public static class Builder {

      private URI elasticsearchUrl;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setElasticsearchUrl(URI elasticsearchUrl) {
        this.elasticsearchUrl = elasticsearchUrl;
        return this;
      }
    }

    public final URI elasticsearchUrl;

    private Settings(Builder builder) {
      this.elasticsearchUrl = builder.elasticsearchUrl;
    }
  }

  public static KibanaModule create(Config config) {
    return new KibanaModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public KibanaModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }

  @Provides
  Client provideClient() {
    return ClientBuilder.newClient();
  }

  @Provides
  @Named(ELASTICSEARCH)
  WebTarget provideWebTarget(Client client) {
    return client.target(settings.elasticsearchUrl);
  }

}
