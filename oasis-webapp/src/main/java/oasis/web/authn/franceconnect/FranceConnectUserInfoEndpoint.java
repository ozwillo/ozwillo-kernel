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

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import oasis.auth.FranceConnectModule;
import oasis.model.authn.AccessToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.Scopes;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;

@Authenticated @OAuth @WithScopes(Scopes.PORTAL)
@Path("/a/franceconnect/userinfo")
@Produces(MediaType.APPLICATION_JSON)
public class FranceConnectUserInfoEndpoint {
  @Inject FranceConnectModule.Settings settings;
  @Inject Client httpClient;
  @Inject TokenRepository tokenRepository;

  @Context SecurityContext securityContext;

  @GET
  public Response get() {
    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    Token token = tokenRepository.getToken(Iterables.getLast(accessToken.getAncestorIds()));
    if (!(token instanceof SidToken) || Strings.isNullOrEmpty(((SidToken) token).getFranceconnectAccessToken())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Response response = httpClient.target(settings.userinfoEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ((SidToken) token).getFranceconnectAccessToken())
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .get();
    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
      // XXX: log 404/410, or 3xx responses? How about 407?
      return Response.serverError().build();
    }
    return response;
  }

  @POST
  public Response post() {
    return get();
  }
}
