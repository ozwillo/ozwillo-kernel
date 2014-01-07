package oasis.auditlog.log4j.fluentd;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.log4j.Log4JAuditLogService;
import oasis.auditlog.log4j.Log4JSupplier;

public class FluentdLog4JAuditLogModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setFluentdUrl(config.getString("url"))
          .setFluentdTag(config.getString("tag"))
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

    public final String fluentdUrl;
    public final String fluentdTag;

    private Settings(Builder builder) {
      this.fluentdUrl = builder.fluentdUrl;
      this.fluentdTag = builder.fluentdTag;
    }
  }

  public static FluentdLog4JAuditLogModule create(Config config) {
    return new FluentdLog4JAuditLogModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public FluentdLog4JAuditLogModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Log4JSupplier.class).to(FluentdLog4JSupplier.class);
    bind(AuditLogService.class).to(Log4JAuditLogService.class);
  }
}
