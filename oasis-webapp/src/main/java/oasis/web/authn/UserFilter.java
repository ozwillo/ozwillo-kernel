/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.authn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
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
  @Inject javax.inject.Provider<SessionManagementHelper> sessionManagementHelper;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final Cookie sidCookie = requestContext.getCookies().get(
        CookieFactory.getCookieName(COOKIE_NAME, requestContext.getSecurityContext().isSecure()));
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
    final String cookieName = CookieFactory.getCookieName(COOKIE_NAME, requestContext.getSecurityContext().isSecure());
    final Map<String, Cookie> requestCookies = requestContext.getCookies();
    final Map<String, NewCookie> responseCookies = responseContext.getCookies();
    boolean needRefreshBrowserState = false;
    // XXX: do not use the SecurityContext as another filter might have replaced it
    if (requestContext.getProperty(SID_PROP) == null &&
        requestCookies.containsKey(cookieName) &&
        !responseCookies.containsKey(cookieName)) {
      // Remove SID cookie if set but does not identifies a session
      // Note that we make sure not to interfere with resources/filters creating a new session!
      responseContext.getHeaders().add(HttpHeaders.SET_COOKIE,
          CookieFactory.createExpiredCookie(COOKIE_NAME, requestContext.getSecurityContext().isSecure(), true));
    }

    final String browserStateCookieName = CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, requestContext.getSecurityContext().isSecure());
    if ((needRefreshBrowserState || !requestCookies.containsKey(browserStateCookieName)) &&
        !responseCookies.containsKey(browserStateCookieName)) {
      // Ensure existence of browser-state cookie; or refresh it if needed.
      // Note that we make sure not to interfere with resources/filters setting/refreshing the browser-state!
      responseContext.getHeaders().add(HttpHeaders.SET_COOKIE,
          SessionManagementHelper.createBrowserStateCookie(requestContext.getSecurityContext().isSecure(), sessionManagementHelper.get().generateBrowserState()));
    }

    responseContext.getHeaders().add(HttpHeaders.VARY, HttpHeaders.COOKIE);
    responseContext.getHeaders().add(HttpHeaders.CACHE_CONTROL, "private");
  }
}
