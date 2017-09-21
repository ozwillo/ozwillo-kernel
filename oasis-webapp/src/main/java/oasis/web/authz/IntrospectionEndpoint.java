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
package oasis.web.authz;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.AppAdminHelper;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Authenticated @Client
@Path("/a/tokeninfo")
public class IntrospectionEndpoint {
  @Inject TokenHandler tokenHandler;
  @Inject ScopeRepository scopeRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AccessControlRepository accessControlRepository;

  @Context SecurityContext securityContext;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response post(@FormParam("token") String token) throws IOException {
    if (Strings.isNullOrEmpty(token)) {
      return error();
    }

    AccessToken accessToken = tokenHandler.getCheckedToken(token, AccessToken.class);

    if (accessToken == null) {
      return error();
    }

    IntrospectionResponse introspectionResponse;
    long issuedAtTime = accessToken.getCreationTime().getMillis();
    long expireAt = accessToken.getExpirationTime().getMillis();

    // Remove all scopes which don't belong to the application instance
    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    final Set<String> scopeIds = Streams.stream(scopeRepository.getScopesOfAppInstance(client_id))
        .map(Scope::getId)
        .filter(accessToken.getScopeIds()::contains)
        .collect(Collectors.toSet());

    if (scopeIds.isEmpty()) {
      return error();
    }

    introspectionResponse = new IntrospectionResponse()
        .setActive(true)
        .setExp(TimeUnit.MILLISECONDS.toSeconds(expireAt))
        .setIat(TimeUnit.MILLISECONDS.toSeconds(issuedAtTime))
        .setScope(scopeIds.stream()
                .filter(Objects::nonNull)
                .collect(joining(" ")))
        .setClient_id(accessToken.getServiceProviderId())
        .setSub(accessToken.getAccountId())
        .setToken_type("Bearer");
    if (ClientIds.DATACORE.equals(client_id)) {
      ArrayList<String> groups = Lists.newArrayList(
          organizationMembershipRepository.getOrganizationIdsForUser(accessToken.getAccountId()));
      AppInstance appInstance = appInstanceRepository.getAppInstance(accessToken.getServiceProviderId());
      if (appInstance != null) {
        if (appAdminHelper.isAdmin(accessToken.getAccountId(), appInstance)) {
          groups.add("app_admin_" + appInstance.getId());
        }
        if (accessControlRepository.getAccessControlEntry(appInstance.getId(), accessToken.getAccountId()) != null) {
          groups.add("app_user_" + appInstance.getId());
        }
      } /* else:
           that shouldn't happen: app_instance has disappeared but there are still valid tokens out there.
           ignore the situation (do not return error()) as that would result in a different answer (active/inactive token)
           depending on whether the request is coming from the DataCore vs. any other client.
         */
      introspectionResponse.setSub_groups(groups);
    }

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
