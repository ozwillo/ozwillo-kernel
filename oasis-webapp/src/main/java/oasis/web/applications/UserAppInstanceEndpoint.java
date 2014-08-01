package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Authenticated @OAuth
@Path("/apps/instance/user/{user_id}")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "user-instances", description = "Application instances for a user")
public class UserAppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;

  @Context SecurityContext securityContext;

  @PathParam("user_id") String userId;

  @GET
  @ApiOperation(
      value = "Retrieve all app instances created by a user",
      response = AppInstance.class,
      responseContainer = "Array"
  )
  public Response get() {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!oAuthUserId.equals(userId)) {
      return ResponseFactory.forbidden("Current user does not match the one in the url");
    }
    Iterable<AppInstance> appInstances = appInstanceRepository.findByInstantiatorId(userId);
    return Response.ok(appInstances).build();
  }
}
