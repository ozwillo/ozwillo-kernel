package oasis.jongo.guice;

import org.jongo.Jongo;

import com.google.inject.AbstractModule;
import com.mongodb.MongoClientURI;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.accounts.JongoAccountRepository;
import oasis.jongo.applications.JongoApplicationRepository;
import oasis.jongo.applications.JongoSubscriptionRepository;
import oasis.jongo.authn.JongoTokenRepository;
import oasis.jongo.authz.JongoAuthorizationRepository;
import oasis.jongo.directory.JongoDirectoryRepository;
import oasis.jongo.etag.JongoEtagService;
import oasis.jongo.notification.JongoNotificationRepository;
import oasis.jongo.social.JongoIdentityRepository;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.SubscriptionRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.directory.DirectoryRepository;
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
          .setMongoUri(new MongoClientURI(config.getString("oasis.mongo.uri")))
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
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(TokenRepository.class).to(JongoTokenRepository.class);
    bind(SubscriptionRepository.class).to(JongoSubscriptionRepository.class);
    bind(EtagService.class).to(JongoEtagService.class);
  }
}
