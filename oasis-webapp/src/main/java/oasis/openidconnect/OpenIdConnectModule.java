package oasis.openidconnect;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

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

      return Settings.builder()
          .setKeyPair(KeyPairLoader.loadOrGenerateKeyPair(privateKeyPath, publicKeyPath))
          .setAccessTokenExpirationSeconds(config.getLong("oasis.oauth.access-token-expiration-seconds"))
          .setIdTokenExpirationSeconds(config.getLong("oasis.openid-connect.id-token-expiration-seconds"))
          .build();
    }

    public static class Builder {

      private KeyPair keyPair;
      private long accessTokenExpirationSeconds;
      private long idTokenExpirationSeconds;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        return this;
      }

      public Builder setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        return this;
      }

      public Builder setIdTokenExpirationSeconds(long idTokenExpirationSeconds) {
        this.idTokenExpirationSeconds = idTokenExpirationSeconds;
        return this;
      }
    }

    public final KeyPair keyPair;
    public final long accessTokenExpirationSeconds;
    public final long idTokenExpirationSeconds;

    private Settings(Builder builder) {
      this.keyPair = builder.keyPair;
      this.accessTokenExpirationSeconds = builder.accessTokenExpirationSeconds;
      this.idTokenExpirationSeconds = builder.idTokenExpirationSeconds;
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
