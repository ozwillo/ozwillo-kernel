package oasis.openidconnect;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.joda.time.Duration;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.security.KeyPairLoader;

public class OpenIdConnectModule extends AbstractModule {
  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      // TODO: refactor to use a single subtree of the config
      Path privateKeyPath = null;
      Path publicKeyPath = null;

      if (config.hasPath("oasis.conf-dir")) {
        Path confDir = Paths.get(config.getString("oasis.conf-dir"));
        privateKeyPath = confDir.resolve(config.getString("oasis.openid-connect.private-key-path"));
        publicKeyPath = confDir.resolve(config.getString("oasis.openid-connect.public-key-path"));
      }

      URI landingPage = null;
      if (config.hasPath("oasis.openid-connect.landing-page")) {
        landingPage = URI.create(config.getString("oasis.openid-connect.landing-page"));
      }

      return Settings.builder()
          .setKeyPair(KeyPairLoader.loadOrGenerateKeyPair(privateKeyPath, publicKeyPath))
          .setAuthorizationCodeDuration(Duration.millis(config.getDuration("oasis.oauth.authorization-code-duration", TimeUnit.MILLISECONDS)))
          .setAccessTokenDuration(Duration.millis(config.getDuration("oasis.oauth.access-token-duration", TimeUnit.MILLISECONDS)))
          .setRefreshTokenDuration(Duration.millis(config.getDuration("oasis.oauth.refresh-token-duration", TimeUnit.MILLISECONDS)))
          .setIdTokenDuration(Duration.millis(config.getDuration("oasis.openid-connect.id-token-duration", TimeUnit.MILLISECONDS)))
          .setSidTokenDuration(Duration.millis(config.getDuration("oasis.session.max-idle-timeout", TimeUnit.MILLISECONDS)))
          .setDisableRedirectUriValidation(config.getBoolean("oasis.openid-connect.disable-redirect-uri-validation"))
          .setLandingPage(landingPage)
          .build();
    }

    public static class Builder {

      private KeyPair keyPair;
      private Duration authorizationCodeDuration;
      private Duration accessTokenDuration;
      private Duration refreshTokenDuration;
      private Duration idTokenDuration;
      private Duration sidTokenDuration;
      private boolean disableRedirectUriValidation;
      private @Nullable URI landingPage;

      public Settings build() {
        return new Settings(this);
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

      public Builder setDisableRedirectUriValidation(boolean disableRedirectUriValidation) {
        this.disableRedirectUriValidation = disableRedirectUriValidation;
        return this;
      }

      public Builder setLandingPage(@Nullable URI landingPage) {
        this.landingPage = landingPage;
        return this;
      }
    }

    public final KeyPair keyPair;
    public final Duration authorizationCodeDuration;
    public final Duration accessTokenDuration;
    public final Duration refreshTokenDuration;
    public final Duration idTokenDuration;
    public final Duration sidTokenDuration;
    public final boolean disableRedirectUriValidation;
    public final @Nullable URI landingPage;

    private Settings(Builder builder) {
      this.keyPair = builder.keyPair;
      this.authorizationCodeDuration = builder.authorizationCodeDuration;
      this.accessTokenDuration = builder.accessTokenDuration;
      this.refreshTokenDuration = builder.refreshTokenDuration;
      this.idTokenDuration = builder.idTokenDuration;
      this.sidTokenDuration = builder.sidTokenDuration;
      this.disableRedirectUriValidation = builder.disableRedirectUriValidation;
      this.landingPage = builder.landingPage;
    }
  }

  public static OpenIdConnectModule create(Config config) {
    return new OpenIdConnectModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public OpenIdConnectModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }
}
