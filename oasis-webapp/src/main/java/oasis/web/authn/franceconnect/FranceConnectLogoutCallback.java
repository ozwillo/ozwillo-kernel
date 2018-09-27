/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.web.authn.franceconnect;

import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import oasis.auth.RedirectUri;
import oasis.model.applications.v2.ServiceRepository;
import oasis.urls.BaseUrls;
import oasis.web.authn.LoginPage;

@Path("/a/franceconnect/postlogout")
public class FranceConnectLogoutCallback {
  private static final Logger logger = LoggerFactory.getLogger(FranceConnectLogoutCallback.class);

  @Inject ServiceRepository serviceRepository;
  @Inject BaseUrls baseUrls;

  @Context UriInfo uriInfo;
  @Context HttpHeaders httpHeaders;
  @Context SecurityContext securityContext;

  @QueryParam("state") String stateKey;

  @GET
  public Response get() {
    Cookie stateCookie = httpHeaders.getCookies().get(FranceConnectLogoutState.getCookieName(stateKey, securityContext.isSecure()));
    if (stateCookie == null) {
      // TODO: proper error page ("check that cookies are enabled in your browser")
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    final FranceConnectLogoutState state = FranceConnectLogoutState.parse(stateCookie.getValue());
    if (state == null) {
      // TODO: proper error page
      return Response.status(Response.Status.BAD_REQUEST)
          .cookie(FranceConnectLogoutState.createExpiredCookie(stateKey, securityContext.isSecure()))
          .build();
    }

    if (state.instanceId() == null || state.post_logout_redirect_uri() == null) {
      return redirectTo(null);
    }

    if (serviceRepository.getServiceByPostLogoutRedirectUri(state.instanceId(), state.post_logout_redirect_uri()) == null) {
      logger.debug("No service found for app instance {} and post_logout_redirect_uri {}",
          state.instanceId(), state.post_logout_redirect_uri());
      return redirectTo(null);
    }

    // Note: validate the URI even if it's in the whitelist, just in case. You can never be too careful.
    if (!RedirectUri.isValid(state.post_logout_redirect_uri())) {
      logger.debug("Invalid post_logout_redirect_uri {}", state.post_logout_redirect_uri());
      return redirectTo(null);
    }

    if (!Strings.isNullOrEmpty(state.state())) {
      return redirectTo(URI.create(new RedirectUri(state.post_logout_redirect_uri())
          .setState(state.state())
          .toString()));
    }
    return redirectTo(URI.create(state.post_logout_redirect_uri()));
  }

  private Response redirectTo(@Nullable URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(baseUrls.landingPage(), uriInfo);
    }
    return Response.seeOther(continueUrl)
        .cookie(FranceConnectLogoutState.createExpiredCookie(stateKey, securityContext.isSecure()))
        .build();
  }
}
