package oasis.web.guice;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.inject.AbstractModule;

import oasis.etag.EtagService;
import oasis.etag.JongoEtagService;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.SubscriptionRepository;
import oasis.model.auth.TokenRepository;
import oasis.model.authorizations.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.notification.NotificationRepository;
import oasis.model.social.IdentityRepository;
import oasis.services.accounts.JongoAccountRepository;
import oasis.services.applications.JongoApplicationRepository;
import oasis.services.applications.JongoSubscriptionRepository;
import oasis.services.auth.JongoTokenRepository;
import oasis.services.authorizations.JongoAuthorizationRepository;
import oasis.services.directory.JongoDirectoryRepository;
import oasis.services.notification.JongoNotificationRepository;
import oasis.services.social.JongoIdentityRepository;

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
