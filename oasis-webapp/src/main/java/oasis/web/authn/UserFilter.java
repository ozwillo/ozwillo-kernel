package oasis.web.authn;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.cookies.CookieFactory;
import oasis.web.utils.UserAgentFingerprinter;

@User
@Provider
@Priority(Priorities.AUTHENTICATION - 1)
public class UserFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String SID_PROP = UserFilter.class.getName() + ".sidToken";

  static final String COOKIE_NAME = "SID";

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject UserAgentFingerprinter fingerprinter;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final Cookie sidCookie = requestContext.getCookies().get(COOKIE_NAME);
    if (sidCookie == null) {
      requestContext.removeProperty(SID_PROP);
      return;
    }
    SidToken sidToken = tokenHandler.getCheckedToken(sidCookie.getValue(), SidToken.class);
    requestContext.setProperty(SID_PROP, sidToken);
    if (sidToken == null) {
      return;
    }

    if (sidToken.getUserAgentFingerprint() != null && !Arrays.equals(fingerprinter.fingerprint(requestContext), sidToken.getUserAgentFingerprint())) {
      // Only the same user agent that created the session can use it
      // XXX: should we forcibly revoke the session if another user agent tries to use it?
      // TODO: log!
      requestContext.removeProperty(SID_PROP);
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

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    // XXX: do not use the SecurityContext as another filter might have replaced it
    if (requestContext.getProperty(SID_PROP) == null &&
        requestContext.getCookies().containsKey(COOKIE_NAME) &&
        !responseContext.getCookies().containsKey(COOKIE_NAME)) {
      // Remove SID cookie if set but does not identifies a session
      // Note that we make sure not to interfere with resources/filters creating a new session!
      responseContext.getHeaders().add(HttpHeaders.SET_COOKIE,
          CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, requestContext.getSecurityContext().isSecure()));
    }
    responseContext.getHeaders().add(HttpHeaders.VARY, HttpHeaders.COOKIE);
    responseContext.getHeaders().add(HttpHeaders.CACHE_CONTROL, "private");
  }
}
