package oasis.auth;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

import org.joda.time.Duration;

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
          .setKeyPair(KeyPairLoader.loadOrGenerateKeyPair(privateKeyPath, publicKeyPath))
          .setAuthorizationCodeDuration(Duration.millis(config.getDuration("authorization-code-duration", TimeUnit.MILLISECONDS)))
          .setAccessTokenDuration(Duration.millis(config.getDuration("access-token-duration", TimeUnit.MILLISECONDS)))
          .setRefreshTokenDuration(Duration.millis(config.getDuration("refresh-token-duration", TimeUnit.MILLISECONDS)))
          .setIdTokenDuration(Duration.millis(config.getDuration("id-token-duration", TimeUnit.MILLISECONDS)))
          .setSidTokenDuration(Duration.millis(config.getDuration("sid-token-duration", TimeUnit.MILLISECONDS)))
          .setAccountActivationTokenDuration(Duration.millis(config.getDuration("account-activation-token-duration", TimeUnit.MILLISECONDS)))
          .setChangePasswordTokenDuration(Duration.millis(config.getDuration("change-password-token-duration", TimeUnit.MILLISECONDS)))
          .setPasswordMinimumLength(config.getInt("password-minimum-length"))
          .build();
    }

    public static class Builder {

      private KeyPair keyPair;
      private Duration authorizationCodeDuration;
      private Duration accessTokenDuration;
      private Duration refreshTokenDuration;
      private Duration idTokenDuration;
      private Duration sidTokenDuration;
      private Duration accountActivationTokenDuration;
      private Duration changePasswordTokenDuration;
      private int passwordMinimumLength;

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

      public Builder setAccountActivationTokenDuration(Duration accountActivationTokenDuration) {
        this.accountActivationTokenDuration = accountActivationTokenDuration;
        return this;
      }

      public Builder setChangePasswordTokenDuration(Duration changePasswordTokenDuration) {
        this.changePasswordTokenDuration = changePasswordTokenDuration;
        return this;
      }

      public Builder setPasswordMinimumLength(int passwordMinimumLength) {
        this.passwordMinimumLength = passwordMinimumLength;
        return this;
      }
    }

    public final KeyPair keyPair;
    public final Duration authorizationCodeDuration;
    public final Duration accessTokenDuration;
    public final Duration refreshTokenDuration;
    public final Duration idTokenDuration;
    public final Duration sidTokenDuration;
    public final Duration accountActivationTokenDuration;
    public final Duration changePasswordTokenDuration;
    public final int passwordMinimumLength;

    private Settings(Builder builder) {
      this.keyPair = builder.keyPair;
      this.authorizationCodeDuration = builder.authorizationCodeDuration;
      this.accessTokenDuration = builder.accessTokenDuration;
      this.refreshTokenDuration = builder.refreshTokenDuration;
      this.idTokenDuration = builder.idTokenDuration;
      this.sidTokenDuration = builder.sidTokenDuration;
      this.accountActivationTokenDuration = builder.accountActivationTokenDuration;
      this.changePasswordTokenDuration = builder.changePasswordTokenDuration;
      this.passwordMinimumLength = builder.passwordMinimumLength;
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
  }
}
