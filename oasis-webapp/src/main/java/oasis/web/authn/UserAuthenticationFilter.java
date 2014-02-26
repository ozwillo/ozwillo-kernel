package oasis.web.authn;

import java.io.IOException;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.cookies.CookieFactory;

@Authenticated @User
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserAuthenticationFilter implements ContainerRequestFilter {

  static final String COOKIE_NAME = "SID";

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final Cookie sidCookie = requestContext.getCookies().get(COOKIE_NAME);
    if (sidCookie == null) {
      // TODO: One-Time Password
      loginResponse(requestContext);
      return;
    }
    SidToken sidToken = tokenHandler.getCheckedToken(sidCookie.getValue(), SidToken.class);
    if (sidToken == null) {
      loginResponse(requestContext);
      return;
    }
    // Renew the token each time the user tries to access a resource
    // XXX: Renew only if the token hasn't been recently created/renewed?
    tokenRepository.renewToken(sidToken.getId());

    final UserSessionPrincipal userSessionPrincipal = new UserSessionPrincipal(sidToken);

    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public UserSessionPrincipal getUserPrincipal() {
        return userSessionPrincipal;
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

  private void loginResponse(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .seeOther(UriBuilder
            .fromResource(LoginPage.class)
            .queryParam(LoginPage.CONTINUE_PARAM, requestContext.getUriInfo().getRequestUri())
            .build())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
        .cookie(CookieFactory.createExpiredCookie(COOKIE_NAME, requestContext.getSecurityContext().isSecure()))
        .build());
  }
}
