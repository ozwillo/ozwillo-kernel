package oasis.web.userdirectory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/d/memberships/membership/{membership_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "memberships", description = "Organization membership")
public class MembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("membership_id") String membershipId;

  @GET
  @ApiOperation(
      value = "Retrieves information about an organization membership",
      response = OrganizationMembership.class
  )
  public Response get() {
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(membershipId);
    if (membership == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    // TODO: check access rights on the membership
    return Response.ok()
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  @DELETE
  @ApiOperation("Deletes a membership")
  public Response delete(@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      ResponseFactory.preconditionRequiredIfMatch();
    }
    boolean deleted;
    try {
      deleted = organizationMembershipRepository.deleteOrganizationMembership(membershipId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (!deleted) {
      return ResponseFactory.NOT_FOUND;
    }
    return ResponseFactory.NO_CONTENT;
  }

  @PUT
  @ApiOperation(
      value = "Updates an organization membership",
      response = OrganizationMembership.class
  )
  public Response put(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
      MembershipRequest request) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      ResponseFactory.preconditionRequiredIfMatch();
    }
    // TODO: make a single, atomic update request to the repository
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(membershipId);
    if (membership == null) {
      return ResponseFactory.NOT_FOUND;
    }
    membership.setAdmin(request.admin);
    try {
      membership = organizationMembershipRepository.updateOrganizationMembership(membership, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (membership == null) {
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok()
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  static class MembershipRequest {
    @JsonProperty boolean admin;
  }
}
