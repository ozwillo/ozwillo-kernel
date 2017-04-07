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
package oasis.web.applications;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.utils.ResponseFactory;

@Path("/apps/acl/ace/{ace_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class AccessControlEntryEndpoint {
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("ace_id") String ace_id;

  @GET
  public Response get() {
    final AccessControlEntry ace = accessControlRepository.getAccessControlEntry(ace_id);
    if (ace == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!ace.getInstance_id().equals(accessToken.getServiceProviderId()) && !ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      return ResponseFactory.forbidden("Cannot read an access control entry about another instance");
    }
    final String userId = accessToken.getAccountId();
    if (!isAppAdmin(userId, ace.getInstance_id())) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
    }

    return Response.ok()
        .tag(etagService.getEtag(ace))
        .entity(new AccessControlEntry(ace) {
          {
            setId(ace.getId());

            if (userId.equals(ace.getUser_id())) {
              app_admin = true; // already checked above
            } else if (isAppAdmin(ace.getUser_id(), ace.getInstance_id())) {
              app_admin = true;
            }
          }

          // TODO: make app_user conditional to… being an app_user (when we'll have that information)
          @JsonProperty boolean app_user = true;
          @JsonProperty Boolean app_admin;
        })
        .build();
  }

  @DELETE
  @WithScopes(Scopes.PORTAL)
  public Response revoke(@HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    AccessControlEntry ace = accessControlRepository.getAccessControlEntry(ace_id);
    if (ace == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!isAppAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), ace.getInstance_id())) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
    }

    boolean deleted;
    try {
      deleted = accessControlRepository.deleteAccessControlEntry(ace_id, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException ive) {
      return ResponseFactory.preconditionFailed(ive.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.NOT_FOUND;
    }

    return ResponseFactory.NO_CONTENT;
  }

  private boolean isAppAdmin(String userId, String instanceId) {
    return appAdminHelper.isAdmin(userId, appInstanceRepository.getAppInstance(instanceId));
  }
}
