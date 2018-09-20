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

import java.util.Objects;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Streams;

import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;

@Path("/d/memberships/user/{user_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Portal
public class UserMembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("user_id") String userId;

  @GET
  public Response get(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    if (!Objects.equals(userId, ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getOrganizationsForUser(userId, start, limit);
    return Response.ok()
        .entity(new GenericEntity<Stream<UserMembership>>(Streams.stream(memberships).map(
            input -> {
              UserMembership membership = new UserMembership();
              membership.id = input.getId();
              membership.membership_uri = uriInfo.getBaseUriBuilder().path(MembershipEndpoint.class).build(input.getId()).toString();
              membership.membership_etag = etagService.getEtag(input).toString();
              membership.organization_id = input.getOrganizationId();
              final Organization organization = directoryRepository.getOrganization(input.getOrganizationId());
              membership.organization_name = organization == null ? null : organization.getName();
              membership.admin = input.isAdmin();
              return membership;
            })) {})
        .build();
  }

  static class UserMembership {
    @JsonProperty String id;
    @JsonProperty String membership_uri;
    @JsonProperty String membership_etag;
    @JsonProperty String organization_id;
    @JsonProperty String organization_name;
    @JsonProperty boolean admin;
  }
}
