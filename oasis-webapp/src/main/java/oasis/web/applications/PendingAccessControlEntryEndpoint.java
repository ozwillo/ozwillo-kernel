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
package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Strings;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.TokenRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/pending-acl/ace/{ace_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class PendingAccessControlEntryEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;
  @Inject TokenRepository tokenRepository;

  @Context SecurityContext securityContext;

  @PathParam("ace_id") String ace_id;

  @DELETE
  public Response deletePendingACE(@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    AccessControlEntry entry = accessControlRepository.getPendingAccessControlEntry(ace_id);
    if (entry == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AppInstance instance = appInstanceRepository.getAppInstance(entry.getInstance_id());
    if (instance == null) {
      // That's not supposed to happen, delete the ACE and do as if it never existed
      accessControlRepository.deletePendingAccessControlEntry(ace_id);
      tokenRepository.revokeInvitationTokensForAppInstance(ace_id);
      return ResponseFactory.NOT_FOUND;
    }

    String currentUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(currentUserId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the app-instance");
    }

    try {
      accessControlRepository.deletePendingAccessControlEntry(ace_id, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionRequired(e.getMessage());
    }
    tokenRepository.revokeInvitationTokensForAppInstance(ace_id);

    return ResponseFactory.NO_CONTENT;
  }
}
