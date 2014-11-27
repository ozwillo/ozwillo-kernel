package oasis.http;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import oasis.web.providers.JacksonJsonProvider;

public class HttpClientModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides @Singleton Client provideClient() {
    return ClientBuilder.newBuilder()
        .register(JacksonJsonProvider.class)
        .build();
  }
}
