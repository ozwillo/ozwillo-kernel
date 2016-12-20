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
package oasis.web.userdirectory;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import oasis.model.authz.Scopes;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.utils.ResponseFactory;

@Path("/d")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
public class UserDirectoryEndpoint {

  @Inject DirectoryRepository directory;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  @GET
  @Path("/org")
  public Response findOrganization(@QueryParam("dc_id") String dc_id) {
    Organization organization = directory.findOrganizationByDcId(dc_id);
    if (organization == null) {
      return ResponseFactory.notFound("No organization found");
    }
    return Response.ok()
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .contentLocation(UriBuilder.fromResource(OrganizationEndpoint.class).build(organization.getId()))
        .build();
  }

  @POST
  @Path("/org")
  public Response createOrganization(@Context SecurityContext securityContext, Organization organization) {
    organization.setStatus(Organization.Status.AVAILABLE);
    organization = directory.createOrganization(organization);

    // Automatically add the current user as an admin for the organization
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrganizationId(organization.getId());
    membership.setAccountId(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    membership.setAdmin(true);
    membership.setStatus(OrganizationMembership.Status.ACCEPTED);
    organizationMembershipRepository.createOrganizationMembership(membership);

    // XXX: add link to org membership that we just created?
    URI uri = UriBuilder.fromResource(OrganizationEndpoint.class).build(organization.getId());
    return Response
        .created(uri)
        .contentLocation(uri)
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
  }
}
