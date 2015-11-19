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
package oasis.web.userdirectory;

import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

import oasis.model.DuplicateKeyException;
import oasis.model.InvalidVersionException;
import oasis.model.authz.Scopes;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.usecases.ChangeOrganizationStatus;
import oasis.usecases.ImmutableChangeOrganizationStatus;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/d/org/{organizationId}")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationEndpoint {

  @Inject DirectoryRepository directory;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject ChangeOrganizationStatus changeOrganizationStatus;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("organizationId") String organizationId;

  @GET
  public Response getOrganization() {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }
    return Response
        .ok()
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
  }

  @PUT
  @Authenticated @OAuth
  @WithScopes(Scopes.PORTAL)
  public Response updateOrganization(
      @Context UriInfo uriInfo,
      @HeaderParam("If-Match") String etagStr,
      Organization organization) {

    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    if (!isOrgAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin of the organization");
    }

    Organization updatedOrganization;
    try {
      updatedOrganization = directory.updateOrganization(organizationId, organization, etagService.parseEtag(etagStr));
    } catch (DuplicateKeyException e) {
      return Response.status(Response.Status.CONFLICT).build();
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (updatedOrganization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }

    URI uri = Resteasy1099.getBaseUriBuilder(uriInfo)
        .path(OrganizationEndpoint.class)
        .build(organizationId);
    return Response.ok(uri)
        .tag(etagService.getEtag(updatedOrganization))
        .contentLocation(uri)
        .entity(updatedOrganization)
        .build();
  }

  @POST
  @Authenticated @OAuth
  @WithScopes(Scopes.PORTAL)
  public Response changeOrganizationStatus(
      @HeaderParam("If-Match") String etagStr,
      ChangeOrganizationStatusRequest request
  ) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    if (!isOrgAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin of the organization");
    }

    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }

    Response error = request.checkStatus();
    if (error != null) {
      return error;
    }

    ImmutableChangeOrganizationStatus.Request changeOrganizationStatusRequest = ImmutableChangeOrganizationStatus.Request.builder()
        .organization(organization)
        .requesterId(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())
        .newStatus(request.status)
        .ifMatch(etagService.parseEtag(etagStr))
        .build();
    ChangeOrganizationStatus.Response changeOrganizationStatusResponse = changeOrganizationStatus.updateStatus(changeOrganizationStatusRequest);

    switch (changeOrganizationStatusResponse.responseStatus()) {
      case SUCCESS:
      case NOTHING_TO_MODIFY:
        return Response.ok(changeOrganizationStatusResponse.organization())
            .tag(etagService.getEtag(changeOrganizationStatusResponse.organization()))
            .build();
      case VERSION_CONFLICT:
        return ResponseFactory.preconditionFailed("Invalid version for organization " + organizationId);
      case ORGANIZATION_PROVIDES_APPLICATIONS:
        // TODO: I18N
        return ResponseFactory.forbidden("Organization provides an application.");
      case ORGANIZATION_HAS_APPLICATION_INSTANCES:
        // TODO: I18N
        return ResponseFactory.forbidden("Organization has running (or pending) application instances.");
      default:
        return Response.serverError().build();
    }
  }

  private boolean isOrgAdmin() {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), organizationId);
    return membership != null && membership.isAdmin();
  }

  public static class ChangeOrganizationStatusRequest {
    @JsonProperty Organization.Status status;

    @Nullable protected Response checkStatus() {
      if (status == null) {
        return ResponseFactory.unprocessableEntity("Instantiation status must be provided.");
      }
      return null;
    }
  }
}
