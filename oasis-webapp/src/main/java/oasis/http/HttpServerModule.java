/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
          .setPort(config.getInt("port"))
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
