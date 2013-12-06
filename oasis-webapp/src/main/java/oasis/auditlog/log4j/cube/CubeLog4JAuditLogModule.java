package oasis.auditlog.log4j.cube;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.log4j.Log4JAuditLogService;
import oasis.auditlog.log4j.Log4JSupplier;

public class CubeLog4JAuditLogModule extends AbstractModule {

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {

      private String cubeUrl;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setCubeUrl(String cubeUrl) {
        this.cubeUrl = cubeUrl;
        return this;
      }
    }

    public final String cubeUrl;

    private Settings(Builder builder) {
      this.cubeUrl = builder.cubeUrl;
    }
  }

  public static CubeLog4JAuditLogModule build(Config config) {
    return new CubeLog4JAuditLogModule(Settings.builder()
        .setCubeUrl(config.getString("oasis.auditlog.cube.url"))
        .build());
  }

  private final Settings settings;

  private CubeLog4JAuditLogModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Log4JSupplier.class).to(CubeLog4JSupplier.class);
    bind(AuditLogService.class).to(Log4JAuditLogService.class);
  }
}
