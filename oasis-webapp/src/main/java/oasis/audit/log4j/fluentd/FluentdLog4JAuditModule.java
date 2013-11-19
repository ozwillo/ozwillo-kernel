package oasis.audit.log4j.fluentd;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.audit.AuditService;
import oasis.audit.log4j.Log4JAuditService;
import oasis.audit.log4j.Log4JSupplier;

public class FluentdLog4JAuditModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setFluentdUrl(config.getString("oasis.audit.fluentd.url"))
          .setFluentdTag(config.getString("oasis.audit.fluentd.tag"))
          .build();
    }

    public static class Builder {

      private String fluentdUrl;
      private String fluentdTag;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setFluentdUrl(String fluentdUrl) {
        this.fluentdUrl = fluentdUrl;
        return this;
      }

      public Builder setFluentdTag(String fluentdTag) {
        this.fluentdTag = fluentdTag;
        return this;
      }
    }

    final public String fluentdUrl;
    final public String fluentdTag;

    private Settings(Builder builder) {
      this.fluentdUrl = builder.fluentdUrl;
      this.fluentdTag = builder.fluentdTag;
    }
  }

  public static FluentdLog4JAuditModule create(Config config) {
    return new FluentdLog4JAuditModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public FluentdLog4JAuditModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Log4JSupplier.class).to(FluentdLog4JSupplier.class);
    bind(AuditService.class).to(Log4JAuditService.class);
  }
}
