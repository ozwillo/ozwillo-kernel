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
import oasis.jongo.applications.JongoApplicationRepository;
import oasis.jongo.applications.v2.JongoAppInstanceRepository;
import oasis.jongo.applications.v2.JongoScopeRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
import oasis.jongo.authn.JongoCredentialsRepository;
import oasis.jongo.authn.JongoTokenRepository;
import oasis.jongo.authz.JongoAuthorizationRepository;
import oasis.jongo.directory.JongoDirectoryRepository;
import oasis.jongo.etag.JongoEtagService;
import oasis.jongo.eventbus.JongoSubscriptionRepository;
import oasis.jongo.notification.JongoNotificationRepository;
import oasis.jongo.social.JongoIdentityRepository;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.eventbus.SubscriptionRepository;
import oasis.model.notification.NotificationRepository;
import oasis.model.social.IdentityRepository;
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
    bind(IdentityRepository.class).to(JongoIdentityRepository.class);
    bind(DirectoryRepository.class).to(JongoDirectoryRepository.class);
    bind(NotificationRepository.class).to(JongoNotificationRepository.class);
    bind(ApplicationRepository.class).to(JongoApplicationRepository.class);
    bind(oasis.model.applications.v2.ApplicationRepository.class).to(oasis.jongo.applications.v2.JongoApplicationRepository.class);
    bind(AppInstanceRepository.class).to(JongoAppInstanceRepository.class);
    bind(ServiceRepository.class).to(JongoServiceRepository.class);
    bind(ScopeRepository.class).to(JongoScopeRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(TokenRepository.class).to(JongoTokenRepository.class);
    bind(SubscriptionRepository.class).to(JongoSubscriptionRepository.class);
    bind(CredentialsRepository.class).to(JongoCredentialsRepository.class);
    bind(EtagService.class).to(JongoEtagService.class);

    Multibinder<JongoBootstrapper> bootstrappers = newSetBinder(binder(), JongoBootstrapper.class);
    bootstrappers.addBinding().to(JongoAccountRepository.class);
    bootstrappers.addBinding().to(JongoIdentityRepository.class);
    bootstrappers.addBinding().to(JongoDirectoryRepository.class);
    bootstrappers.addBinding().to(JongoNotificationRepository.class);
    bootstrappers.addBinding().to(JongoApplicationRepository.class);
    bootstrappers.addBinding().to(JongoAuthorizationRepository.class);
    bootstrappers.addBinding().to(JongoTokenRepository.class);
    bootstrappers.addBinding().to(JongoSubscriptionRepository.class);
    bootstrappers.addBinding().to(JongoCredentialsRepository.class);
    bootstrappers.addBinding().to(oasis.jongo.applications.v2.JongoApplicationRepository.class);
    bootstrappers.addBinding().to(JongoAppInstanceRepository.class);
    bootstrappers.addBinding().to(JongoServiceRepository.class);
    bootstrappers.addBinding().to(JongoScopeRepository.class);
  }
}
