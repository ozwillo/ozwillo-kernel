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

    public Builder setNettyPort(int nettyPort) {
      this.nettyPort = nettyPort;
      return this;
    }

    public Builder setSwaggerApiVersion(String swaggerApiVersion) {
      this.swaggerApiVersion = swaggerApiVersion;
      return this;
    }

    public Settings build() {
      return new Settings(this);
    }
  }

  public final int nettyPort;

  public final String swaggerApiVersion;

  private Settings(Builder builder) {
    this.nettyPort = builder.nettyPort;
    this.swaggerApiVersion = builder.swaggerApiVersion;
  }
}
