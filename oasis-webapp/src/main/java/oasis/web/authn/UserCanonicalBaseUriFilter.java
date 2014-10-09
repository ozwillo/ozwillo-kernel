package oasis.web.authn;

import java.io.IOException;
import java.net.URI;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import oasis.openidconnect.OpenIdConnectModule;
import oasis.web.resteasy.Resteasy1099;

/**
 * Enforce the use of the {@link OpenIdConnectModule.Settings#canonicalBaseUri canonical base URI}
 * for cookie-based authentication.
 */
@User
@Provider
@Priority(0)
public class UserCanonicalBaseUriFilter implements ContainerRequestFilter {

  @Inject OpenIdConnectModule.Settings settings;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (settings.canonicalBaseUri == null) {
      return; // nothing to do
    }
    if (requestContext.getUriInfo().getBaseUri().equals(settings.canonicalBaseUri)) {
      return; // we're already on the canonical base URI
    }

    URI relativeUri = Resteasy1099.getBaseUri(requestContext.getUriInfo()).relativize(requestContext.getUriInfo().getRequestUri());
    URI canonicalUri = settings.canonicalBaseUri.resolve(relativeUri);

    requestContext.abortWith(Response.status(Response.Status.MOVED_PERMANENTLY)
        .location(canonicalUri)
        .build());
  }
}
