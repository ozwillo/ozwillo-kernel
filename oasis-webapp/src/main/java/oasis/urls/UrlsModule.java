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
package oasis.urls;

import java.net.URI;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.typesafe.config.Config;

public class UrlsModule extends AbstractModule {

  private final Urls urls;

  public UrlsModule(Urls urls) {
    this.urls = urls;
  }

  public static UrlsModule create(Config config) {
    return new UrlsModule(ImmutableUrls.builder()
        .canonicalBaseUri(get(config, "canonical-base-uri"))
        .landingPage(get(config, "landing-page"))
        .myOasis(get(config, "my-oasis"))
        .myProfile(get(config, "my-profile"))
        .myApps(get(config, "my-apps"))
        .myNetwork(get(config, "my-network"))
        .build());
  }

  private static Optional<URI> get(Config config, String key) {
    if (!config.hasPath(key)) {
      return Optional.absent();
    }
    return Optional.of(URI.create(config.getString(key)));
  }

  @Override
  protected void configure() {
    bind(Urls.class).toInstance(urls);
  }
}
