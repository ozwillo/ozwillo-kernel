package oasis.web;

import javax.inject.Singleton;

@Singleton
public class Settings {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private int nettyPort;

    public Builder setNettyPort(int nettyPort) {
      this.nettyPort = nettyPort;
      return this;
    }

    public Settings build() {
      return new Settings(this);
    }
  }

  public final int nettyPort;

  private Settings(Builder builder) {
    this.nettyPort = builder.nettyPort;
  }
}
