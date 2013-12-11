package oasis.web.authn;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

@Authenticated @User
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserAuthenticationFilter implements ContainerRequestFilter {

  static final String COOKIE_NAME = "SID";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final Cookie sid = requestContext.getCookies().get(COOKIE_NAME);
    if (sid == null) {
      // TODO: One-Time Password
      requestContext.abortWith(Response
          .seeOther(UriBuilder
              .fromResource(Login.class)
              .queryParam(Login.CONTINUE_PARAM, requestContext.getUriInfo().getRequestUri())
              .build())
          .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
          .header("Pragma", "no-cache")
          .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
          .build());
      return;
    }

    // TODO: validate SID

    final AccountPrincipal accountPrincipal = new AccountPrincipal(sid.getValue());

    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public AccountPrincipal getUserPrincipal() {
        return accountPrincipal;
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
