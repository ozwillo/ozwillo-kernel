/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import java.util.Locale;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;

import org.immutables.value.Value;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

import net.ltgt.resteasy.client.okhttp3.OkHttpClientEngine;
import oasis.web.providers.JacksonJsonProvider;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

@Value.Enclosing
public class HttpClientModule extends AbstractModule {
  private static final Logger logger = LoggerFactory.getLogger(HttpClientModule.class);

  @Value.Immutable
  public interface Settings {
    HttpLoggingInterceptor.Level loggingLevel();
  }

  public static HttpClientModule create(Config config) {
    return new HttpClientModule(ImmutableHttpClientModule.Settings.builder()
        .loggingLevel(HttpLoggingInterceptor.Level.valueOf(config.getString("logging-level").toUpperCase(Locale.ROOT)))
        .build());
  }

  private final Settings settings;

  public HttpClientModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() { }

  @Provides @Singleton Client provideClient(OkHttpClient okHttpClient) {
    return new ResteasyClientBuilder()
        .httpEngine(new OkHttpClientEngine(okHttpClient))
        .register(JacksonJsonProvider.class)
        .build();
  }

  @Provides @Singleton OkHttpClient provideOkHttpClient() {
    return new OkHttpClient.Builder()
        .followRedirects(false)
        .addNetworkInterceptor(
            new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
              @Override
              public void log(String message) {
                logger.info(message);
              }
            })
            .setLevel(settings.loggingLevel()))
        .build();
  }
}
