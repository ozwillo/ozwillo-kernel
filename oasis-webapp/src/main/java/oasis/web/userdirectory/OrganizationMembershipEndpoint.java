package oasis.web.userdirectory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authz.Scopes;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/d/memberships/org/{organization_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
@Api(value = "memberships-org", description = "Organization Memberships (from the organization point of view)")
public class OrganizationMembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  @ApiOperation(
      value = "Retrieves users who are members of the organization",
      response = OrgMembership.class,
      responseContainer = "Array"
  )
  public Response get(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), organizationId);
    if (membership == null || !membership.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin for the organization");
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getMembersOfOrganization(organizationId, start, limit);
    return toResponse(memberships);
  }

  @GET
  @Path("/admins")
  public Response getAdmins(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), organizationId);
    if (membership == null) {
      return ResponseFactory.forbidden("Current user is not a member of the organization");
    }
    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organizationId, start, limit);
    return toResponse(admins);
  }

  private Response toResponse(Iterable<OrganizationMembership> memberships) {
    return Response.ok()
        .entity(new GenericEntity<Iterable<OrgMembership>>(Iterables.transform(memberships,
            new Function<OrganizationMembership, OrgMembership>() {
              @Override
              public OrgMembership apply(OrganizationMembership input) {
                OrgMembership membership = new OrgMembership();
                membership.id = input.getId();
                membership.membership_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(MembershipEndpoint.class).build(input.getId()).toString();
                membership.membership_etag = etagService.getEtag(input);
                membership.account_id = input.getAccountId();
                // TODO: check access rights to the user name
                final UserAccount account = accountRepository.getUserAccountById(input.getAccountId());
                membership.account_name = account == null ? null : account.getDisplayName();
                membership.admin = input.isAdmin();
                return membership;
              }
            })) {})
        .build();
  }

  @POST
  @ApiOperation(
      value = "Creates an organization membership; either account_id or email has to be given",
      notes = "For now, email must match an existing account",
      response = OrganizationMembership.class
  )
  public Response post(MembershipRequest request) {
    String requesterId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership ownerMembership = organizationMembershipRepository.getOrganizationMembership(requesterId, organizationId);
    if (ownerMembership == null || !ownerMembership.isAdmin()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrganizationId(organizationId);
    if (!Strings.isNullOrEmpty(request.account_id)) {
      // TODO: check existence of the user
      membership.setAccountId(request.account_id);
    } else {
      UserAccount userAccount = accountRepository.getUserAccountByEmail(request.email);
      if (userAccount != null) {
        membership.setAccountId(userAccount.getId());
      } else {
        // TODO: invite user
        return ResponseFactory.unprocessableEntity("No account found for this email address");
      }
    }
    // TODO: notify user for approval
    membership.setAdmin(request.admin);
    membership.setCreated(Instant.now());
    membership.setCreator_id(requesterId);
    membership = organizationMembershipRepository.createOrganizationMembership(membership);
    if (membership == null) {
      return Response.status(Response.Status.CONFLICT).build();
    }
    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(MembershipEndpoint.class).build(membership.getId()))
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  static class OrgMembership {
    @JsonProperty String id;
    @JsonProperty String membership_uri;
    @JsonProperty String membership_etag;
    @JsonProperty String account_id;
    @JsonProperty String account_name;
    @JsonProperty boolean admin;
  }

  static class MembershipRequest {
    @JsonProperty String account_id;
    @JsonProperty String email;
    @JsonProperty boolean admin;
  }
}
