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

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import oasis.urls.Urls;
import oasis.web.resteasy.Resteasy1099;

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
    if (!urls.canonicalBaseUri().isPresent()) {
      return; // nothing to do
    }
    if (Resteasy1099.getBaseUri(requestContext.getUriInfo()).equals(urls.canonicalBaseUri().get())) {
      return; // we're already on the canonical base URI
    }

    URI relativeUri = Resteasy1099.getBaseUri(requestContext.getUriInfo()).relativize(requestContext.getUriInfo().getRequestUri());
    URI canonicalUri = urls.canonicalBaseUri().get().resolve(relativeUri);

    requestContext.abortWith(Response.seeOther(canonicalUri).build());
  }
}
