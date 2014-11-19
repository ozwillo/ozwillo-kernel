package oasis.web.guice;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import oasis.services.authn.login.PasswordHasher;
import oasis.services.authn.login.SCryptPasswordHasher;
import oasis.soy.SoyGuiceModule;
import oasis.web.providers.JacksonJsonProvider;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(JsonFactory.class).to(JacksonFactory.class);
    bind(Clock.class).toInstance(Clock.SYSTEM);

    bind(PasswordHasher.class).to(SCryptPasswordHasher.class);

    install(new SoyGuiceModule());
  }

  @Provides @Singleton Client provideClient() {
    return ClientBuilder.newBuilder()
        .register(JacksonJsonProvider.class)
        .build();
  }
}
