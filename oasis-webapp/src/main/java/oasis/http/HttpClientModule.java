package oasis.http;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;

import org.immutables.value.Value;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.URLConnectionEngine;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

import oasis.web.providers.JacksonJsonProvider;

public class HttpClientModule extends AbstractModule {

  @Override
  protected void configure() { }

  @Provides @Singleton Client provideClient() {
    return new ResteasyClientBuilder()
        .httpEngine(new URLConnectionEngine())
        .register(JacksonJsonProvider.class)
        .build();
  }
}
