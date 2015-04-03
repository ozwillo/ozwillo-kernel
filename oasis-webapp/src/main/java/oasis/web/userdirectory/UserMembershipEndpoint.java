package oasis.web.userdirectory;

import java.util.Objects;

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
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

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
import oasis.web.resteasy.Resteasy1099;

@Path("/d/memberships/user/{user_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
@Api(value = "memberships-user", description = "Organization Memberships (from the user point of view)")
public class UserMembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("user_id") String userId;

  @GET
  @ApiOperation(
      value = "Retrieves organizations the user is a member of",
      response = UserMembership.class,
      responseContainer = "Array"
  )
  public Response get(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    if (!Objects.equals(userId, ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getOrganizationsForUser(userId, start, limit);
    return Response.ok()
        .entity(new GenericEntity<Iterable<UserMembership>>(Iterables.transform(memberships,
            new Function<OrganizationMembership, UserMembership>() {
              @Override
              public UserMembership apply(OrganizationMembership input) {
                UserMembership membership = new UserMembership();
                membership.id = input.getId();
                membership.membership_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(MembershipEndpoint.class).build(input.getId()).toString();
                membership.membership_etag = etagService.getEtag(input);
                membership.organization_id = input.getOrganizationId();
                final Organization organization = directoryRepository.getOrganization(input.getOrganizationId());
                membership.organization_name = organization == null ? null : organization.getName();
                membership.admin = input.isAdmin();
                return membership;
              }
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
