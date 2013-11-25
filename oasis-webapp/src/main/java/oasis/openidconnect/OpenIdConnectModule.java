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
      Path privateKeyPath = null;
      Path publicKeyPath = null;

      if (config.hasPath("oasis.conf-dir")) {
        Path confDir = Paths.get(config.getString("oasis.conf-dir"));
        privateKeyPath = confDir.resolve(config.getString("oasis.openid-connect.private-key-path"));
        publicKeyPath = confDir.resolve(config.getString("oasis.openid-connect.public-key-path"));
      }

      return Settings.builder()
          .setKeyPair(KeyPairLoader.loadOrGenerateKeyPair(privateKeyPath, publicKeyPath))
          .build();
    }

    public static class Builder {

      private KeyPair keyPair;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        return this;
      }
    }

    final public KeyPair keyPair;

    private Settings(Builder builder) {
      this.keyPair = builder.keyPair;
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
