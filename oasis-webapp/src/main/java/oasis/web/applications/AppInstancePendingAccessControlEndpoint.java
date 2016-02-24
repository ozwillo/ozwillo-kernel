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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/pending-acl/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class AppInstancePendingAccessControlEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("instance_id") String instance_id;

  @GET
  public Response getPendingMemberships() {
    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String accountId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(accountId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin");
    }
    Iterable<AccessControlEntry> entries = accessControlRepository.getPendingAccessControlListForAppInstance(instance_id);
    return toResponse(entries);
  }

  private Response toResponse(Iterable<AccessControlEntry> memberships) {
    return Response.ok()
        .entity(new GenericEntity<Iterable<PendingACE>>(Iterables.transform(memberships,
            new Function<AccessControlEntry, PendingACE>() {
              @Override
              public PendingACE apply(AccessControlEntry accessControlEntry) {
                PendingACE ace = new PendingACE();
                ace.id = accessControlEntry.getId();
                ace.pending_entry_uri = uriInfo.getBaseUriBuilder()
                    .path(PendingAccessControlEntryEndpoint.class)
                    .build(accessControlEntry.getId())
                    .toString();
                ace.pending_entry_etag = etagService.getEtag(accessControlEntry);
                ace.email = accessControlEntry.getEmail();
                ace.created = accessControlEntry.getCreated();
                return ace;
              }
            })) {})
        .build();
  }

  static class PendingACE {
    @JsonProperty String id;
    @JsonProperty String pending_entry_uri;
    @JsonProperty String pending_entry_etag;
    @JsonProperty String email;
    @JsonProperty Instant created;
  }
}
