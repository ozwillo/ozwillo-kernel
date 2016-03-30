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
package oasis.web.authn;

import java.io.IOException;
import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import oasis.urls.Urls;

/**
 * Enforce the use of the {@link oasis.urls.Urls#canonicalBaseUri() canonical base URI}
 * for cookie-based authentication.
 */
@User
@Provider
@Priority(0)
public class UserCanonicalBaseUriFilter implements ContainerRequestFilter {

  @Inject Urls urls;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    @Nullable Response response = maybeRedirectToCanonicalUri(urls, requestContext.getUriInfo());
    if (response != null) {
      requestContext.abortWith(response);
    }
  }

  /**
   * Returns a {@link Response} redirecting to the canonical URI for the given {@code UriInfo},
   * or {@code null} if it already is canonical.
   */
  static @Nullable Response maybeRedirectToCanonicalUri(Urls urls, UriInfo uriInfo) {
    if (!urls.canonicalBaseUri().isPresent()) {
      return null; // nothing to do
    }
    if (uriInfo.getBaseUri().equals(urls.canonicalBaseUri().get())) {
      return null; // we're already on the canonical base URI
    }

    URI relativeUri = uriInfo.getBaseUri().relativize(uriInfo.getRequestUri());
    URI canonicalUri = urls.canonicalBaseUri().get().resolve(relativeUri);
    return Response.seeOther(canonicalUri).build();
  }
}
