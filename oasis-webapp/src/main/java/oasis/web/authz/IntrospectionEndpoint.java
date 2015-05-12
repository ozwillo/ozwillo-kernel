/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
package oasis.web.authz;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.authn.AccessToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.GroupService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Authenticated @Client
@Path("/a/tokeninfo")
@Api(value = "/a/tokeninfo", description = "Introspection Endpoint")
public class IntrospectionEndpoint {
  private static final Joiner SCOPE_JOINER = Joiner.on(' ').skipNulls();

  @Context SecurityContext securityContext;
  @Inject TokenHandler tokenHandler;
  @Inject AccountRepository accountRepository;
  @Inject ScopeRepository scopeRepository;
  @Inject GroupService groupService;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Get information about an access token.",
      notes = "See the <a href=\"http://tools.ietf.org/html/draft-richer-oauth-introspection\">DRAFT</a> for more information.",
      response = IntrospectionResponse.class
  )
  public Response post(@FormParam("token") String token) throws IOException {
    if (Strings.isNullOrEmpty(token)) {
      return error();
    }

    AccessToken accessToken = tokenHandler.getCheckedToken(token, AccessToken.class);

    if (accessToken == null) {
      return error();
    }

    UserAccount account = accountRepository.getUserAccountById(accessToken.getAccountId());

    IntrospectionResponse introspectionResponse;
    long issuedAtTime = accessToken.getCreationTime().getMillis();
    long expireAt = accessToken.getExpirationTime().getMillis();

    final Set<String> scopeIds = Sets.newHashSet(accessToken.getScopeIds());
    // Remove all scopes which don't belong to the application instance
    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    scopeIds.retainAll(FluentIterable.from(scopeRepository.getScopesOfAppInstance(client_id))
        .transform(new Function<Scope, String>() {
          @Override
          public String apply(Scope scope) {
            return scope.getId();
          }
        })
        .toList());

    if (scopeIds.isEmpty()) {
      return error();
    }

    introspectionResponse = new IntrospectionResponse()
        .setActive(true)
        .setExp(TimeUnit.MILLISECONDS.toSeconds(expireAt))
        .setIat(TimeUnit.MILLISECONDS.toSeconds(issuedAtTime))
        .setScope(SCOPE_JOINER.join(scopeIds))
        .setClient_id(accessToken.getServiceProviderId())
        .setSub(account.getId())
        .setToken_type("Bearer")
        .setSub_groups(groupService.getGroups(account));

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity(introspectionResponse)
        .build();
  }

  private Response error() {
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity(new IntrospectionResponse().setActive(false))
        .build();
  }
}
