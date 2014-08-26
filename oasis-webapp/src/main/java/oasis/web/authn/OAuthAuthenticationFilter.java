package oasis.web.authn;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import oasis.model.authn.AccessToken;
import oasis.services.authn.TokenHandler;

/**
 * Implements Bearer Token authentication.
 * <p>
 * Works in conjunction with {@link OAuthFilter} and blocks requests without
 * an {@code Authorization: Bearer …} header.
 *
 * @see <a href='http://tools.ietf.org/html/rfc6750'>OAuth 2.0 Bearer Token Usage</a>
 * @see OAuthFilter
 */
@Authenticated @OAuth
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OAuthAuthenticationFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    Principal principal = requestContext.getSecurityContext().getUserPrincipal();
    if (principal == null) {
      challenge(requestContext);
    }
  }

  private void challenge(ContainerRequestContext requestContext) {
    requestContext.abortWith(challengeResponse());
  }

  public static Response challengeResponse() {
    return Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, OAuthFilter.AUTH_SCHEME)
        .build();
  }
}
