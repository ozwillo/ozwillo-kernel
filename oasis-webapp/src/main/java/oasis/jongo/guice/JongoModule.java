package oasis.jongo.guice;

import org.jongo.Jongo;

import com.google.inject.AbstractModule;
import com.mongodb.MongoClientURI;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;

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

    final public MongoClientURI mongoURI;

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
  }
}
