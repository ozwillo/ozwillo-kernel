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
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.services.authz.AppAdminHelper;
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
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("instance_id") String instance_id;

  @GET
  @ApiOperation(
      value = "Retrieves app_users of the app instance",
      response = ACE.class,
      responseContainer = "Array"
  )
  public Response get() {
    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
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
                final UserAccount user = accountRepository.getUserAccountById(input.getUser_id());
                ace.user_name = user == null ? null : user.getDisplayName();
                ace.creator_id = input.getCreator_id();
                final UserAccount creator = accountRepository.getUserAccountById(input.getCreator_id());
                ace.creator_name = creator == null ? null : creator.getDisplayName();
                return ace;
              }
            })) {})
        .build();
  }

  @POST
  @ApiOperation(
      value = "Add an app_user to the app instance",
      response = ACE.class,
      responseContainer = "Array"
  )
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
    if (!appAdminHelper.isAdmin(currentUserId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
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
