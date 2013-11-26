package oasis.web.guice;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;

import oasis.model.applications.ApplicationRepository;
import oasis.model.authorizations.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.services.applications.DummyApplicationRepository;
import oasis.services.authorizations.JongoAuthorizationRepository;
import oasis.services.directory.DummyDirectoryRepository;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
    bind(ApplicationRepository.class).to(DummyApplicationRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(JsonFactory.class).to(JacksonFactory.class);
    bind(Clock.class).toInstance(Clock.SYSTEM);
  }
}
