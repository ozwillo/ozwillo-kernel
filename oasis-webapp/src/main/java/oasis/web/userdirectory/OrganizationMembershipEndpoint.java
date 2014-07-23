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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;
import oasis.web.utils.UserAgentFingerprinter;

@Path("/d/memberships/org/{organization_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class OrganizationMembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  public Response get(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(organizationId, ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    if (membership == null || !membership.isAdmin()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getMembersOfOrganization(organizationId, start, limit);
    return Response.ok()
        .entity(new GenericEntity<Iterable<OrgMembership>>(Iterables.transform(memberships,
            new Function<OrganizationMembership, OrgMembership>() {
              @Override
              public OrgMembership apply(OrganizationMembership input) {
                OrgMembership membership = new OrgMembership();
                membership.id = input.getId();
                membership.membership_uri = uriInfo.getBaseUriBuilder().path(MembershipEndpoint.class).build(input.getId()).toString();
                membership.membership_etag = etagService.getEtag(input);
                membership.account_id = input.getAccountId();
                // TODO: check access rights to the user name
                membership.account_name = accountRepository.getUserAccountById(input.getOrganizationId()).getName();
                membership.admin = input.isAdmin();
                return membership;
              }
            })) {})
        .build();
  }

  @POST
  public Response post(MembershipRequest request) {
    OrganizationMembership ownerMembership = organizationMembershipRepository
        .getOrganizationMembership(organizationId, ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
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
    membership = organizationMembershipRepository.createOrganizationMembership(membership);
    if (membership == null) {
      return Response.status(Response.Status.CONFLICT).build();
    }
    return Response.created(uriInfo.getBaseUriBuilder().path(MembershipEndpoint.class).build(membership.getId()))
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
