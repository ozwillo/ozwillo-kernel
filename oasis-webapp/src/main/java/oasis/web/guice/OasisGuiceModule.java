package oasis.web.guice;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;

import oasis.services.authn.login.PasswordHasher;
import oasis.services.authn.login.SCryptPasswordHasher;
import oasis.soy.SoyGuiceModule;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(JsonFactory.class).to(JacksonFactory.class);
    bind(Clock.class).toInstance(Clock.SYSTEM);

    bind(PasswordHasher.class).to(SCryptPasswordHasher.class);

    install(new SoyGuiceModule());
  }
}
