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
