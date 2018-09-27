/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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

import javax.annotation.Nullable;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class MyUrls implements Urls {

  private Optional<URI> portalBaseUri;
  private final PathUrls pathUrls;
  private final BaseUrls baseUrls;

  @Inject
  public MyUrls(@Assisted @Nullable String portalBaseUri, PathUrls pathUrls, BaseUrls baseUrls) {
    this.portalBaseUri = portalBaseUri == null ? baseUrls.portalBaseUri() : Optional.of(URI.create(portalBaseUri));
    this.pathUrls = pathUrls;
    this.baseUrls = baseUrls;
  }

  @Override
  public Optional<URI> canonicalBaseUri() {
    return baseUrls.canonicalBaseUri();
  }

  @Override
  public Optional<URI> landingPage() {
    return baseUrls.landingPage();
  }

  @Override
  public Optional<URI> portalBaseUri() {
    return portalBaseUri;
  }

  @Override
  public Optional<URI> myOasis() {
    return this.resolve(portalBaseUri, pathUrls.myOasis());
  }

  @Override
  public Optional<URI> myProfile() {
    return this.resolve(portalBaseUri, pathUrls.myProfile());
  }

  @Override
  public Optional<URI> popupProfile() {
    return this.resolve(portalBaseUri, pathUrls.popupProfile());
  }

  @Override
  public Optional<URI> myApps() {
    return this.resolve(portalBaseUri, pathUrls.myApps());
  }

  @Override
  public Optional<URI> myNetwork() {
    return this.resolve(portalBaseUri, pathUrls.myNetwork());
  }

  @Override
  public Optional<URI> developerDoc() {
    return baseUrls.developerDoc();
  }

  @Override
  public Optional<URI> privacyPolicy() {
    return baseUrls.privacyPolicy();
  }

  @Override
  public Optional<URI> termsOfService() {
    return baseUrls.termsOfService();
  }

  private Optional<URI> resolve(Optional<URI> baseUri, Optional<String> path){
    return baseUri.flatMap(uri -> path.map(uri::resolve));
  }

}
