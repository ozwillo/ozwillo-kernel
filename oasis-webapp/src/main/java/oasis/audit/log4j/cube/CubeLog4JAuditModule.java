package oasis.audit.log4j.cube;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import oasis.audit.AuditService;
import oasis.audit.log4j.Log4JAuditService;
import oasis.audit.log4j.Log4JSupplier;

public class CubeLog4JAuditModule extends AbstractModule {

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

    final public String cubeUrl;

    private Settings(Builder builder) {
      this.cubeUrl = builder.cubeUrl;
    }
  }

  public static CubeLog4JAuditModule build(Config config) {
    return new CubeLog4JAuditModule(Settings.builder()
        .setCubeUrl(config.getString("oasis.audit.cube.url"))
        .build());
  }

  private final Settings settings;

  private CubeLog4JAuditModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
    bind(Log4JSupplier.class).to(CubeLog4JSupplier.class);
    bind(AuditService.class).to(Log4JAuditService.class);
  }
}
