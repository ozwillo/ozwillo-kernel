package oasis.web.authn;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import oasis.services.cookies.CookieFactory;

@Authenticated @User
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserAuthenticationFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    Principal principal = requestContext.getSecurityContext().getUserPrincipal();
    if (principal == null) {
      // TODO: One-Time Password
      loginResponse(requestContext);
    }
  }

  private void loginResponse(ContainerRequestContext requestContext) {
    requestContext.abortWith(loginResponse(requestContext.getUriInfo().getRequestUri(), null, requestContext.getSecurityContext()));
  }

  public static Response loginResponse(URI continueUrl, @Nullable String cancelUrl, SecurityContext securityContext) {
    final UriBuilder redirectUri = UriBuilder
        .fromResource(LoginPage.class)
        .queryParam(LoginPage.CONTINUE_PARAM, continueUrl);
    if (cancelUrl != null) {
      redirectUri.queryParam(LoginPage.CANCEL_PARAM, cancelUrl);
    }
    return Response
        .seeOther(redirectUri.build())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure()))
        .build();
  }
}
