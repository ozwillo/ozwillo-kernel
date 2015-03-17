package oasis.jongo.guice;

import static com.google.inject.multibindings.Multibinder.*;

import org.jongo.Jongo;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.mongodb.MongoClientURI;
import com.typesafe.config.Config;

import oasis.jongo.JongoBootstrapper;
import oasis.jongo.JongoService;
import oasis.jongo.accounts.JongoAccountRepository;
import oasis.jongo.applications.v2.JongoAccessControlRepository;
import oasis.jongo.applications.v2.JongoAppInstanceRepository;
import oasis.jongo.applications.v2.JongoApplicationRepository;
import oasis.jongo.applications.v2.JongoCatalogEntryRepository;
import oasis.jongo.applications.v2.JongoScopeRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
import oasis.jongo.applications.v2.JongoUserSubscriptionRepository;
import oasis.jongo.authn.JongoCredentialsRepository;
import oasis.jongo.authn.JongoTokenRepository;
import oasis.jongo.authz.JongoAuthorizationRepository;
import oasis.jongo.directory.JongoDirectoryRepository;
import oasis.jongo.directory.JongoOrganizationMembershipRepository;
import oasis.jongo.etag.JongoEtagService;
import oasis.jongo.eventbus.JongoSubscriptionRepository;
import oasis.jongo.notification.JongoNotificationRepository;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntryRepository;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.eventbus.SubscriptionRepository;
import oasis.model.notification.NotificationRepository;
import oasis.services.etag.EtagService;

public class JongoModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setMongoUri(new MongoClientURI(config.getString("uri")))
          .build();
    }

    public static class Builder {

      private MongoClientURI mongoURI;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setMongoUri(MongoClientURI mongoURI) {
        this.mongoURI = mongoURI;
        return this;
      }
    }

    public final MongoClientURI mongoURI;

    private Settings(Builder builder) {
      this.mongoURI = builder.mongoURI;
    }
  }

  public static JongoModule create(Config config) {
    return new JongoModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public JongoModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Jongo.class).toProvider(JongoService.class);

    bind(AccountRepository.class).to(JongoAccountRepository.class);
    bind(DirectoryRepository.class).to(JongoDirectoryRepository.class);
    bind(OrganizationMembershipRepository.class).to(JongoOrganizationMembershipRepository.class);
    bind(NotificationRepository.class).to(JongoNotificationRepository.class);
    bind(CatalogEntryRepository.class).to(JongoCatalogEntryRepository.class);
    bind(ApplicationRepository.class).to(JongoApplicationRepository.class);
    bind(AppInstanceRepository.class).to(JongoAppInstanceRepository.class);
    // ServiceRepository is bound in CatalogModule
    bind(ScopeRepository.class).to(JongoScopeRepository.class);
    bind(UserSubscriptionRepository.class).to(JongoUserSubscriptionRepository.class);
    bind(AccessControlRepository.class).to(JongoAccessControlRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(TokenRepository.class).to(JongoTokenRepository.class);
    bind(SubscriptionRepository.class).to(JongoSubscriptionRepository.class);
    bind(CredentialsRepository.class).to(JongoCredentialsRepository.class);
    bind(EtagService.class).to(JongoEtagService.class);

    Multibinder<JongoBootstrapper> bootstrappers = newSetBinder(binder(), JongoBootstrapper.class);
    bootstrappers.addBinding().to(JongoAccountRepository.class);
    bootstrappers.addBinding().to(JongoDirectoryRepository.class);
    bootstrappers.addBinding().to(JongoOrganizationMembershipRepository.class);
    bootstrappers.addBinding().to(JongoNotificationRepository.class);
    bootstrappers.addBinding().to(JongoAuthorizationRepository.class);
    bootstrappers.addBinding().to(JongoTokenRepository.class);
    bootstrappers.addBinding().to(JongoSubscriptionRepository.class);
    bootstrappers.addBinding().to(JongoCredentialsRepository.class);
    bootstrappers.addBinding().to(JongoApplicationRepository.class);
    bootstrappers.addBinding().to(JongoAppInstanceRepository.class);
    bootstrappers.addBinding().to(JongoServiceRepository.class);
    bootstrappers.addBinding().to(JongoScopeRepository.class);
    bootstrappers.addBinding().to(JongoUserSubscriptionRepository.class);
    bootstrappers.addBinding().to(JongoAccessControlRepository.class);
  }
}
