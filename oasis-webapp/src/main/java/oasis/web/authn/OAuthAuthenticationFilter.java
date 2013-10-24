package oasis.web.authn;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Splitter;

/**
 * Implements Bearer Token authentication.
 *
 * @see <a href='http://tools.ietf.org/html/rfc6750'>OAuth 2.0 Bearer Token Usage</a>
 */
@Authenticated @OAuth
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OAuthAuthenticationFilter implements ContainerRequestFilter {

  private static final String AUTH_SCHEME = "Bearer";

  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      challenge(requestContext);
      return;
    }

    List<String> parts = AUTH_SCHEME_SPLITTER.splitToList(authorization);

    if (parts.isEmpty() || !AUTH_SCHEME.equalsIgnoreCase(parts.get(0))) {
      challenge(requestContext);
      return;
    }
    if (parts.size() != 2) {
      invalidRequest(requestContext);
      return;
    }

    // TODO: get user principal
    // TODO: add configurable 'scope' (will be used when access_token is validated)
    final String userName = parts.get(1);
    final Principal principal = new Principal() {
      @Override
      public String getName() {
        return userName;
      }
    };

    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return principal;
      }

      @Override
      public boolean isUserInRole(String role) {
        return false;
      }

      @Override
      public boolean isSecure() {
        return oldSecurityContext.isSecure();
      }

      @Override
      public String getAuthenticationScheme() {
        return "OAUTH_BEARER";
      }
    });
  }

  private void challenge(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME)
        .build());
  }

  private void invalidRequest(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_request\"")
        .build());
  }
}
