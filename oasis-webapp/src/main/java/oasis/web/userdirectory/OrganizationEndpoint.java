package oasis.web.userdirectory;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import oasis.model.InvalidVersionException;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/d/org/{organizationId}")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "organization", description = "Organization")
public class OrganizationEndpoint {

  @Inject DirectoryRepository directory;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

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

  @DELETE
  @Authenticated @OAuth
  @ApiOperation(value = "Delete an organization")
  public Response deleteOrganization(
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr
  ) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // FIXME: what should we do about the organization's applications/instances/services?

    boolean deleted;
    try {
      deleted = directory.deleteOrganization(organizationId, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.notFound("The requested organization does not exist");
    }

    // XXX: refactor ?
    organizationMembershipRepository.deleteMembershipsInOrganization(organizationId);

    return ResponseFactory.NO_CONTENT;
  }
}
