package oasis.web.userdirectory;

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

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.authn.TokenRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/d/pending-memberships/membership/{membership_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "pending memberships", description = "Pending organization membership")
public class PendingMembershipEndpoint {
  @PathParam("membership_id") String membershipId;

  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject TokenRepository tokenRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @DELETE
  @ApiOperation("Deletes a pending membership")
  public Response deletePendingMembership(@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    OrganizationMembership organizationMembership = organizationMembershipRepository.getPendingOrganizationMembership(membershipId);
    if (organizationMembership == null) {
      return ResponseFactory.NOT_FOUND;
    }

    String currentUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership organizationMembershipRequester = organizationMembershipRepository.getOrganizationMembership(currentUserId,
        organizationMembership.getOrganizationId());
    if (organizationMembershipRequester == null || !organizationMembershipRequester.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin for the organization");
    }

    try {
      organizationMembershipRepository.deletePendingOrganizationMembership(membershipId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionRequired(e.getMessage());
    }
    tokenRepository.revokeInvitationTokensForOrganizationMembership(membershipId);

    return ResponseFactory.NO_CONTENT;
  }
}
