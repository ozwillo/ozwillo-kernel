package oasis.web.authn.testing;

import java.io.IOException;
import java.security.Principal;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;

import oasis.model.authn.SidToken;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;

@User
@Priority(Priorities.AUTHENTICATION - 1)
public class TestUserFilter implements ContainerRequestFilter {
  private final SidToken sidToken;

  public TestUserFilter(SidToken sidToken) {
    this.sidToken = sidToken;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return new UserSessionPrincipal(sidToken);
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
        return SecurityContext.FORM_AUTH;
      }
    });
  }
}
