package oasis.web;

import javax.inject.Singleton;

@Singleton
public class Settings {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private int nettyPort;
    private String swaggerApiVersion;
    private boolean auditDisabled;
    private String auditService;
    private String auditLog4JSupplier;
    private String auditCubeUrl;

    public Builder setNettyPort(int nettyPort) {
      this.nettyPort = nettyPort;
      return this;
    }

    public Builder setSwaggerApiVersion(String swaggerApiVersion) {
      this.swaggerApiVersion = swaggerApiVersion;
      return this;
    }

    public Builder setAuditDisabled(boolean auditDisabled) {
      this.auditDisabled = auditDisabled;
      return this;
    }

    public Builder setAuditService(String auditService) {
      this.auditService = auditService;
      return this;
    }

    public Builder setAuditLog4JSupplier(String auditLog4JSupplier) {
      this.auditLog4JSupplier = auditLog4JSupplier;
      return this;
    }

    public Builder setAuditCubeUrl(String auditCubeUrl) {
      this.auditCubeUrl = auditCubeUrl;
      return this;
    }

    public Settings build() {
      return new Settings(this);
    }
  }

  public final int nettyPort;

  public final String swaggerApiVersion;

  public final boolean auditDisabled;
  public final String auditService;
  public final String auditLog4JSupplier;
  public final String auditCubeUrl;

  private Settings(Builder builder) {
    this.nettyPort = builder.nettyPort;
    this.swaggerApiVersion = builder.swaggerApiVersion;
    this.auditDisabled = builder.auditDisabled;
    this.auditService = builder.auditService;
    this.auditLog4JSupplier = builder.auditLog4JSupplier;
    this.auditCubeUrl = builder.auditCubeUrl;
  }
}
