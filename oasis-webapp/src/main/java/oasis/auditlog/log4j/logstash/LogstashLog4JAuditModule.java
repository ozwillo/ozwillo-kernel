package oasis.auditlog.log4j.logstash;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.log4j.Log4JAuditLogService;
import oasis.auditlog.log4j.Log4JSupplier;

public class LogstashLog4JAuditModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      return Settings.builder()
          .setHost(config.getString("oasis.auditlog.logstash.host"))
          .setPort(config.getInt("oasis.auditlog.logstash.port"))
          .build();
    }

    public static class Builder {

      private String host;

      private int port;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setHost(String host) {
        this.host = host;
        return this;
      }

      public Builder setPort(int port) {
        this.port = port;
        return this;
      }
    }

    final public String host;
    final public int port;

    private Settings(Builder builder) {
      this.host = builder.host;
      this.port = builder.port;
    }
  }

  public static LogstashLog4JAuditModule create(Config config) {
    return new LogstashLog4JAuditModule(Settings.fromConfig(config));
  }

  private final Settings settings;

  public LogstashLog4JAuditModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Log4JSupplier.class).to(LogstashLog4JSupplier.class);
    bind(AuditLogService.class).to(Log4JAuditLogService.class);
  }
}
