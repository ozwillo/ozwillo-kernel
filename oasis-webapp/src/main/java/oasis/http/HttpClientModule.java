package oasis.http;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;

import org.immutables.value.Value;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

import oasis.web.providers.JacksonJsonProvider;

@Value.Nested
public class HttpClientModule extends AbstractModule {

  @Value.Immutable
  public interface Settings {
    int connectionPoolSize();
    int maxPooledPerRoute();
  }

  public static HttpClientModule create(Config config) {
    return new HttpClientModule(ImmutableHttpClientModule.Settings.builder()
        .connectionPoolSize(config.getInt("connection-pool-size"))
        .maxPooledPerRoute(config.getInt("max-pooled-per-route"))
        .build());
  }

  private final Settings settings;

  public HttpClientModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }

  @Provides @Singleton Client provideClient(Settings settings) {
    return new ResteasyClientBuilder()
        .register(JacksonJsonProvider.class)
        .connectionPoolSize(settings.connectionPoolSize())
        .maxPooledPerRoute(settings.maxPooledPerRoute())
        .build();
  }
}
