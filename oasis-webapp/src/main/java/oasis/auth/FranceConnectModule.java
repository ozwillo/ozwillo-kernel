/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.auth;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;

import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

import org.immutables.value.Value;

import okhttp3.HttpUrl;

@Value.Enclosing
public class FranceConnectModule extends AbstractModule {
  @Value.Immutable
  public interface Settings {
    String issuer();
    HttpUrl authorizationEndpoint();
    String tokenEndpoint();
    String userinfoEndpoint();
    HttpUrl endSessionEndpoint();

    String clientId();
    String clientSecret();
  }

  public static FranceConnectModule create(Config config) {
    return new FranceConnectModule(ImmutableFranceConnectModule.Settings.builder()
        .issuer(config.getString("issuer"))
        .authorizationEndpoint(HttpUrl.parse(config.getString("authorization_endpoint")))
        .tokenEndpoint(config.getString("token_endpoint"))
        .userinfoEndpoint(config.getString("userinfo_endpoint"))
        .endSessionEndpoint(HttpUrl.parse(config.getString("end_session_endpoint")))
        .clientId(config.getString("client_id"))
        .clientSecret(config.getString("client_secret"))
        .build());
  }

  private final Settings settings;

  public FranceConnectModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    newOptionalBinder(binder(), Settings.class).setBinding().toInstance(settings);
  }
}
