/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
import oasis.jongo.applications.v2.JongoScopeRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
import oasis.jongo.applications.v2.JongoUserSubscriptionRepository;
import oasis.jongo.authn.JongoClientCertificateRepository;
import oasis.jongo.authn.JongoCredentialsRepository;
import oasis.jongo.authn.JongoJtiRepository;
import oasis.jongo.authn.JongoTokenRepository;
import oasis.jongo.authz.JongoAuthorizationRepository;
import oasis.jongo.branding.JongoBrandRepository;
import oasis.jongo.directory.JongoDirectoryRepository;
import oasis.jongo.directory.JongoOrganizationMembershipRepository;
import oasis.jongo.etag.JongoEtagService;
import oasis.jongo.eventbus.JongoSubscriptionRepository;
import oasis.jongo.notification.JongoNotificationRepository;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.JtiRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.branding.BrandRepository;
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
    // CatalogEntryRepository is bound in CatalogModule
    bind(ApplicationRepository.class).to(JongoApplicationRepository.class);
    bind(AppInstanceRepository.class).to(JongoAppInstanceRepository.class);
    // ServiceRepository is bound in CatalogModule
    bind(ScopeRepository.class).to(JongoScopeRepository.class);
    bind(UserSubscriptionRepository.class).to(JongoUserSubscriptionRepository.class);
    bind(AccessControlRepository.class).to(JongoAccessControlRepository.class);
    bind(AuthorizationRepository.class).to(JongoAuthorizationRepository.class);
    bind(TokenRepository.class).to(JongoTokenRepository.class);
    bind(JtiRepository.class).to(JongoJtiRepository.class);
    bind(SubscriptionRepository.class).to(JongoSubscriptionRepository.class);
    bind(CredentialsRepository.class).to(JongoCredentialsRepository.class);
    bind(EtagService.class).to(JongoEtagService.class);
    bind(ClientCertificateRepository.class).to(JongoClientCertificateRepository.class);
    bind(BrandRepository.class).to(JongoBrandRepository.class);

    Multibinder<JongoBootstrapper> bootstrappers = newSetBinder(binder(), JongoBootstrapper.class);
    bootstrappers.addBinding().to(JongoAccountRepository.class);
    bootstrappers.addBinding().to(JongoDirectoryRepository.class);
    bootstrappers.addBinding().to(JongoOrganizationMembershipRepository.class);
    bootstrappers.addBinding().to(JongoNotificationRepository.class);
    bootstrappers.addBinding().to(JongoAuthorizationRepository.class);
    bootstrappers.addBinding().to(JongoTokenRepository.class);
    bootstrappers.addBinding().to(JongoJtiRepository.class);
    bootstrappers.addBinding().to(JongoSubscriptionRepository.class);
    bootstrappers.addBinding().to(JongoCredentialsRepository.class);
    bootstrappers.addBinding().to(JongoApplicationRepository.class);
    bootstrappers.addBinding().to(JongoAppInstanceRepository.class);
    bootstrappers.addBinding().to(JongoServiceRepository.class);
    bootstrappers.addBinding().to(JongoScopeRepository.class);
    bootstrappers.addBinding().to(JongoUserSubscriptionRepository.class);
    bootstrappers.addBinding().to(JongoAccessControlRepository.class);
    bootstrappers.addBinding().to(JongoClientCertificateRepository.class);
    bootstrappers.addBinding().to(JongoBrandRepository.class);
  }
}
