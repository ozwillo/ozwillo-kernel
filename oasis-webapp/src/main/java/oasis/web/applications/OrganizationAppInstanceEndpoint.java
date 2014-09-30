package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Function;
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
@Path("/apps/instance/organization/{organization_id}")
@Api(value = "organization-instances", description = "Application instances for an organization")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationAppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  @ApiOperation(
      value = "Retrieve all app instances created for an organization",
      response = AppInstance.class,
      responseContainer = "Array"
  )
  public Response get() {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(oAuthUserId, organizationId);
    if (organizationMembership == null) {
      return ResponseFactory.forbidden("Current user is not a member of the organization");
    }
    if (!organizationMembership.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an administrator of target organization");
    }

    Iterable<AppInstance> appInstances = appInstanceRepository.findByOrganizationId(organizationId);
    return Response.ok()
        .entity(new GenericEntity<Iterable<AppInstance>>(
            Iterables.transform(appInstances,
                new Function<AppInstance, AppInstance>() {
                  @Override
                  public AppInstance apply(AppInstance instance) {
                    // XXX: Don't send secrets over the wire
                    instance.setDestruction_secret(null);
                    return instance;
                  }
                })) {})
        .build();
  }
}
