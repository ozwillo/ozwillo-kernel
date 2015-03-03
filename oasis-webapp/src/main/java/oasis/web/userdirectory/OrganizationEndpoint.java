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
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.ApplicationRepository;
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
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/d/org/{organizationId}")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "organization", description = "Organization")
public class OrganizationEndpoint {

  @Inject ApplicationRepository applicationRepository;
  @Inject DirectoryRepository directory;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject ChangeOrganizationStatus changeOrganizationStatus;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("organizationId") String organizationId;

  @GET
  @ApiOperation(value = "Retrieve an organization",
      response = Organization.class)
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
  @ApiOperation(value = "Update an organization",
      response = Organization.class)
  public Response updateOrganization(
      @Context UriInfo uriInfo,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
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
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (updatedOrganization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }

    URI uri = Resteasy1099.getBaseUriBuilder(uriInfo)
        .path(OrganizationEndpoint.class)
        .build(organizationId);
    return Response.created(uri)
        .tag(etagService.getEtag(updatedOrganization))
        .contentLocation(uri)
        .entity(updatedOrganization)
        .build();
  }

  @POST
  @Authenticated @OAuth
  @ApiOperation(value = "Change organization status")
  public Response changeOrganizationStatus(
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      ChangeOrganizationStatusRequest request
  ) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    if (!isOrgAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin of the organization");
    }

    // FIXME: what should we do about the organization's applications/instances/services?

    // TODO: Create a usecase object
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }

    Response error = request.checkStatus();
    if (error != null) {
      return error;
    }

    if (applicationRepository.getCountByProvider(organizationId) > 0) {
      return ResponseFactory.conflict("Can't remove organization related to an application");
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
      case CHANGE_APP_INSTANCE_STATUS_ERROR:
        return Response.serverError()
            .entity("Error while stopping related app-instances.")
            .build();
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
