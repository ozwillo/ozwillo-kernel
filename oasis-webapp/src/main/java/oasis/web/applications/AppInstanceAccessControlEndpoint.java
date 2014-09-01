package oasis.web.applications;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/acl/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "acl-instance", description = "Application Instance's Access Control List")
public class AppInstanceAccessControlEndpoint {
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccountRepository accountRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("instance_id") String instance_id;

  @GET
  @ApiOperation(
      value = "Retrieves users subscribed to the service",
      response = ACE.class,
      responseContainer = "Array"
  )
  public Response get() {
    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!isAdminOfOrganization(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance.getProvider_id())) {
      return ResponseFactory.forbidden("Current user is not an admin of the application instance's providing organization");
    }

    Iterable<AccessControlEntry> aces = accessControlRepository.getAccessControlListForAppInstance(instance_id);
    return Response.ok()
        .entity(new GenericEntity<Iterable<ACE>>(Iterables.transform(aces,
            new Function<AccessControlEntry, ACE>() {
              @Override
              public ACE apply(AccessControlEntry input) {
                ACE ace = new ACE();
                ace.id = input.getId();
                ace.entry_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(AccessControlEntryEndpoint.class).build(input.getId()).toString();
                ace.entry_etag = etagService.getEtag(input);
                ace.instance_id = input.getInstance_id();
                ace.user_id = input.getUser_id();
                ace.user_name = accountRepository.getUserAccountById(input.getUser_id()).getName();
                ace.creator_id = input.getCreator_id();
                ace.creator_name = accountRepository.getUserAccountById(input.getCreator_id()).getName();
                return ace;
              }
            })) {})
        .build();
  }

  @POST
  public Response addToList(AccessControlEntry ace) {
    if (ace.getInstance_id() != null && !instance_id.equals(ace.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    ace.setInstance_id(instance_id);
    // TODO: check that the user exists
    if (Strings.isNullOrEmpty(ace.getUser_id())) {
      return ResponseFactory.unprocessableEntity("user_id is missing");
    }

    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String currentUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!isAdminOfOrganization(currentUserId, instance.getProvider_id())) {
      return ResponseFactory.forbidden("Current user is not an admin of the application instance's providing organization");
    }
    ace.setCreator_id(currentUserId);

    ace = accessControlRepository.createAccessControlEntry(ace);
    if (ace == null) {
      return ResponseFactory.conflict("Entry for that user already exists");
    }
    URI aceUri = Resteasy1099.getBaseUriBuilder(uriInfo).path(AccessControlEntryEndpoint.class).build(ace.getId());
    return Response.created(aceUri)
        .contentLocation(aceUri)
        .tag(etagService.getEtag(ace))
        .entity(ace)
        .build();
  }

  private boolean isAdminOfOrganization(String userId, String organizationId) {
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(userId, organizationId);
    if (membership == null) {
      return false;
    }
    return membership.isAdmin();
  }

  static class ACE {
    @JsonProperty String id;
    @JsonProperty String entry_uri;
    @JsonProperty String entry_etag;
    @JsonProperty String instance_id;
    @JsonProperty String user_id;
    @JsonProperty String user_name;
    @JsonProperty String creator_id;
    @JsonProperty String creator_name;
  }
}
