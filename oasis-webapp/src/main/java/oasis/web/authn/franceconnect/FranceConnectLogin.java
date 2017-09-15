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

import static oasis.web.authn.LoginPage.LOCALE_PARAM;

import oasis.auth.FranceConnectModule;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.security.SecureRandom;

import com.ibm.icu.util.ULocale;

@User
@Path("/a/franceconnect/login")
@StrictReferer // TODO: find a way to whitelist the portal
public class FranceConnectLogin {

  @Inject FranceConnectModule.Settings settings;
  @Inject SecureRandom secureRandom;
  @Inject LocaleHelper localeHelper;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;
  @Context Request request;

  @POST
  public Response post(
      @FormParam(LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("continue") URI continueUrl
  ) {
    locale = localeHelper.selectLocale(locale, request);

    final String state = FranceConnectLoginState.generateStateKey(secureRandom);
    final String nonce = FranceConnectLoginState.generateNonce(secureRandom);

    HttpUrl.Builder builder = settings.authorizationEndpoint().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", settings.clientId())
            .addQueryParameter("redirect_uri", uriInfo.getBaseUriBuilder().path(FranceConnectCallback.class).build().toString())
            .addQueryParameter("scope", "openid profile birth email") // TODO
            .addQueryParameter("state", state)
            .addQueryParameter("nonce", nonce);
    if (securityContext.getUserPrincipal() != null) {
      String franceConnectIdToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getFranceconnectIdToken();
      if (franceConnectIdToken != null) {
        builder.addQueryParameter("id_token_hint", franceConnectIdToken);
      }
    }
    return Response.seeOther(builder.build().uri())
        .cookie(FranceConnectLoginState.createCookie(state, locale, nonce, continueUrl, securityContext.isSecure()))
        .build();
  }

}
