package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Authenticated @OAuth
@Path("/apps/instance/user/{user_id}")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "user-instances", description = "Application instances for a user (bought for himself)")
public class UserAppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Context SecurityContext securityContext;

  @PathParam("user_id") String userId;

  @GET
  @ApiOperation(
      value = "Retrieve all app instances created by a user for himself, and/or created for organization the user is an admin of, possibly filtered by instantiation status",
      response = AppInstance.class,
      responseContainer = "Array"
  )
  public Response get(
      @QueryParam("include_orgs") @DefaultValue("false") boolean includeOrgs,
      @QueryParam("status") AppInstance.InstantiationStatus instantiationStatus
  ) {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!oAuthUserId.equals(userId)) {
      return ResponseFactory.forbidden("Current user does not match the one in the url");
    }
    // TODO
    Iterable<AppInstance> appInstances;
    if (instantiationStatus == null) {
      appInstances = appInstanceRepository.findPersonalInstancesByUserId(userId);
    } else {
      appInstances = appInstanceRepository.findPersonalInstancesByUserIdAndStatus(userId,
          instantiationStatus);
    }
    if (includeOrgs) {
      for (OrganizationMembership membership : organizationMembershipRepository.getOrganizationsForAdmin(oAuthUserId)) {
        if (instantiationStatus == null) {
          appInstances = Iterables.concat(appInstances, appInstanceRepository.findByOrganizationId(membership.getOrganizationId()));
        } else {
          appInstances = Iterables.concat(appInstances, appInstanceRepository.findByOrganizationIdAndStatus(membership.getOrganizationId(), instantiationStatus));
        }
      }
    }
    return Response.ok()
        .entity(new GenericEntity<Iterable<AppInstance>>(
            Iterables.transform(appInstances,
                new Function<AppInstance, AppInstance>() {
                  @Override
                  public AppInstance apply(AppInstance instance) {
                    // XXX: Don't send secrets over the wire
                    instance.setDestruction_secret(null);
                    instance.setStatus_changed_secret(null);
                    // XXX: keep the redirect_uri_validation_disabled "secret"
                    instance.unsetRedirect_uri_validation_disabled();
                    return instance;
                  }
                })) {})
        .build();
  }
}
