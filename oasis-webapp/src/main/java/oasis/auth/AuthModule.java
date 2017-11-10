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
package oasis.auth;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Duration;

import javax.annotation.Nullable;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.security.KeyPairLoader;

public class AuthModule extends AbstractModule {
  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      // TODO: store PKIs in DB to use a single subtree of the config
      Path privateKeyPath = null;
      Path publicKeyPath = null;

      if (config.hasPath("oasis.conf-dir")) {
        Path confDir = Paths.get(config.getString("oasis.conf-dir"));
        privateKeyPath = confDir.resolve(config.getString("private-key-path"));
        publicKeyPath = confDir.resolve(config.getString("public-key-path"));
      }

      return Settings.builder()
          .setPortalOrigin(config.hasPath("portal-origin")
              ? config.getString("portal-origin")
              : null)
          .setKeyPair(KeyPairLoader.loadOrGenerateKeyPair(privateKeyPath, publicKeyPath))
          .setAuthorizationCodeDuration(config.getDuration("authorization-code-duration"))
          .setAccessTokenDuration(config.getDuration("access-token-duration"))
          .setRefreshTokenDuration(config.getDuration("refresh-token-duration"))
          .setIdTokenDuration(config.getDuration("id-token-duration"))
          .setSidTokenDuration(config.getDuration("sid-token-duration"))
          .setAccountActivationTokenDuration(config.getDuration("account-activation-token-duration"))
          .setChangePasswordTokenDuration(config.getDuration("change-password-token-duration"))
          .setJwtBearerDuration(config.getDuration("jwt-bearer-duration"))
          .setPasswordMinimumLength(config.getInt("password-minimum-length"))
          .setEnableClientCertificates(config.getBoolean("enable-client-certificates"))
          .build();
    }

    public static class Builder {

      private @Nullable String portalOrigin;
      private KeyPair keyPair;
      private Duration authorizationCodeDuration;
      private Duration accessTokenDuration;
      private Duration refreshTokenDuration;
      private Duration idTokenDuration;
      private Duration sidTokenDuration;
      private Duration accountActivationTokenDuration;
      private Duration changePasswordTokenDuration;
      private Duration jwtBearerDuration;
      private int passwordMinimumLength;
      private boolean enableClientCertificates;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setPortalOrigin(@Nullable String portalOrigin) {
        this.portalOrigin = portalOrigin;
        return this;
      }

      public Builder setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        return this;
      }

      public Builder setAuthorizationCodeDuration(Duration authorizationCodeDuration) {
        this.authorizationCodeDuration = authorizationCodeDuration;
        return this;
      }

      public Builder setAccessTokenDuration(Duration accessTokenDuration) {
        this.accessTokenDuration = accessTokenDuration;
        return this;
      }

      public Builder setRefreshTokenDuration(Duration refreshTokenDuration) {
        this.refreshTokenDuration = refreshTokenDuration;
        return this;
      }

      public Builder setIdTokenDuration(Duration idTokenDuration) {
        this.idTokenDuration = idTokenDuration;
        return this;
      }

      public Builder setSidTokenDuration(Duration sidTokenDuration) {
        this.sidTokenDuration = sidTokenDuration;
        return this;
      }

      public Builder setAccountActivationTokenDuration(Duration accountActivationTokenDuration) {
        this.accountActivationTokenDuration = accountActivationTokenDuration;
        return this;
      }

      public Builder setChangePasswordTokenDuration(Duration changePasswordTokenDuration) {
        this.changePasswordTokenDuration = changePasswordTokenDuration;
        return this;
      }

      public Builder setJwtBearerDuration(Duration jwtBearerDuration) {
        this.jwtBearerDuration = jwtBearerDuration;
        return this;
      }

      public Builder setPasswordMinimumLength(int passwordMinimumLength) {
        this.passwordMinimumLength = passwordMinimumLength;
        return this;
      }

      public Builder setEnableClientCertificates(boolean enableClientCertificates) {
        this.enableClientCertificates = enableClientCertificates;
        return this;
      }
    }

    public @Nullable String portalOrigin;
    public final KeyPair keyPair;
    public final Duration authorizationCodeDuration;
    public final Duration accessTokenDuration;
    public final Duration refreshTokenDuration;
    public final Duration idTokenDuration;
    public final Duration sidTokenDuration;
    public final Duration accountActivationTokenDuration;
    public final Duration changePasswordTokenDuration;
    public final Duration jwtBearerDuration;
    public final int passwordMinimumLength;
    public final boolean enableClientCertificates;

    private Settings(Builder builder) {
      this.portalOrigin = builder.portalOrigin;
      this.keyPair = builder.keyPair;
      this.authorizationCodeDuration = builder.authorizationCodeDuration;
      this.accessTokenDuration = builder.accessTokenDuration;
      this.refreshTokenDuration = builder.refreshTokenDuration;
      this.idTokenDuration = builder.idTokenDuration;
      this.sidTokenDuration = builder.sidTokenDuration;
      this.accountActivationTokenDuration = builder.accountActivationTokenDuration;
      this.changePasswordTokenDuration = builder.changePasswordTokenDuration;
      this.jwtBearerDuration = builder.jwtBearerDuration;
      this.passwordMinimumLength = builder.passwordMinimumLength;
      this.enableClientCertificates = builder.enableClientCertificates;
    }
  }

  public static AuthModule create(Config config) {
    return new AuthModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public AuthModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);

    newOptionalBinder(binder(), FranceConnectModule.Settings.class)
        .setDefault().toProvider(() -> null);
  }
}
