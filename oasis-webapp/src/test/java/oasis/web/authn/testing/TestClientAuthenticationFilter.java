package oasis.web.authn.testing;

import java.io.IOException;
import java.security.Principal;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;

import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Authenticated @Client
@Priority(Priorities.AUTHENTICATION)
public class TestClientAuthenticationFilter implements ContainerRequestFilter {
  private final String clientId;

  public TestClientAuthenticationFilter(String clientId) {
    this.clientId = clientId;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return new ClientPrincipal(clientId);
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
        return SecurityContext.BASIC_AUTH;
      }
    });
  }
}
