package oasis.web.authn.testing;

import java.io.IOException;
import java.security.Principal;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import oasis.model.authn.AccessToken;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;

@Authenticated @OAuth
@Provider
@Priority(Priorities.AUTHENTICATION)
public class TestOAuthAuthenticationFilter implements ContainerRequestFilter {
  private final AccessToken accessToken;

  public TestOAuthAuthenticationFilter(AccessToken accessToken) {
    this.accessToken = accessToken;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return new OAuthPrincipal(accessToken);
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
}
