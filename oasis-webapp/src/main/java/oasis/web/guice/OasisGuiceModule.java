package oasis.web.guice;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;

import oasis.services.etag.EtagService;
import oasis.jongo.etag.JongoEtagService;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.SubscriptionRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.notification.NotificationRepository;
import oasis.model.social.IdentityRepository;
import oasis.jongo.accounts.JongoAccountRepository;
import oasis.jongo.applications.JongoApplicationRepository;
import oasis.jongo.applications.JongoSubscriptionRepository;
import oasis.jongo.authn.JongoTokenRepository;
import oasis.jongo.authz.JongoAuthorizationRepository;
import oasis.jongo.directory.JongoDirectoryRepository;
import oasis.jongo.notification.JongoNotificationRepository;
import oasis.jongo.social.JongoIdentityRepository;

public class OasisGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AccountRepository.class).to(JongoAccountRepository.class);
    bind(IdentityRepository.class).to(JongoIdentityRepository.class);
    bind(DirectoryRepository.class).to(JongoDirectoryRepository.class);
    bind(NotificationRepository.class).to(JongoNotificationRepository.class);
    bind(ApplicationRepository.class).to(JongoApplicationRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(TokenRepository.class).to(JongoTokenRepository.class);
    bind(SubscriptionRepository.class).to(JongoSubscriptionRepository.class);
    bind(EtagService.class).to(JongoEtagService.class);

    bind(JsonFactory.class).to(JacksonFactory.class);
    bind(Clock.class).toInstance(Clock.SYSTEM);
  }
}
