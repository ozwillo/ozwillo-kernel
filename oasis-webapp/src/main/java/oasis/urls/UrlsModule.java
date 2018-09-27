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
package oasis.urls;

import java.net.URI;
import java.util.Optional;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.typesafe.config.Config;

public class UrlsModule extends AbstractModule {

  private final BaseUrls baseUrls;
  private final PathUrls pathUrls;

  public UrlsModule(BaseUrls baseUrls, PathUrls pathUrls) {
    this.baseUrls = baseUrls;
    this.pathUrls = pathUrls;
  }

  public static UrlsModule create(Config config) {
    return new UrlsModule(ImmutableBaseUrls.builder()
        .canonicalBaseUri(get(config, "canonical-base-uri"))
        .landingPage(get(config, "landing-page"))
        .portalBaseUri(get(config, "portal-base-uri"))
        .developerDoc(get(config, "developer-doc"))
        .privacyPolicy(get(config, "privacy-policy"))
        .termsOfService(get(config, "terms-of-service"))
        .build(),
        ImmutablePathUrls.builder()
            .myOasis(getStr(config, "path.my-oasis"))
            .myProfile(getStr(config, "path.my-profile"))
            .popupProfile(getStr(config, "path.popup-profile"))
            .myApps(getStr(config, "path.my-apps"))
            .myNetwork(getStr(config, "path.my-network"))
            .build());
  }

  private static Optional<URI> get(Config config, String key) {
    if (!config.hasPath(key)) {
      return Optional.empty();
    }
    return Optional.of(URI.create(config.getString(key)));
  }

  private static Optional<String> getStr(Config config, String key) {
    if (!config.hasPath(key)) {
      return Optional.empty();
    }
    return Optional.of(config.getString(key));
  }

  @Override
  protected void configure() {
    bind(BaseUrls.class).toInstance(baseUrls);
    bind(PathUrls.class).toInstance(pathUrls);

    install(new FactoryModuleBuilder()
        .implement(Urls.class, MyUrls.class)
        .build(UrlsFactory.class));
  }
}
