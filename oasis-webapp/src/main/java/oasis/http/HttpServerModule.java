package oasis.http;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

public class HttpServerModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setPort(config.getInt("oasis.http.port"))
          .build();
    }

    public static class Builder {

      private int port;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setPort(int port) {
        this.port = port;
        return this;
      }
    }

    public final int nettyPort;

    private Settings(Builder builder) {
      this.nettyPort = builder.port;
    }
  }

  public static HttpServerModule create(Config config) {
    return new HttpServerModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public HttpServerModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }
}
