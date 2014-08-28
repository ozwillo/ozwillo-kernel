package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
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

import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/app/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
@Api(value = "apps", description = "Applications")
public class ApplicationEndpoint {
  @Inject ApplicationRepository applicationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Context SecurityContext securityContext;
  @PathParam("application_id") String applicationId;

  @GET
  @ApiOperation(
      value = "Get information about an application",
      response = Application.class
  )
  public Response getApplication() {
    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application not found");
    }

    if (!application.isVisible()) {
      // only the application admins should be able to see it if it's "hidden"
      OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      if (principal == null) {
        return OAuthAuthenticationFilter.challengeResponse();
      }
      String userId = principal.getAccessToken().getAccountId();
      OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(userId, application.getProvider_id());
      if (organizationMembership == null) {
        return ResponseFactory.forbidden("Current user is not a member of the application's provider organization");
      }
      if (!organizationMembership.isAdmin()) {
        return ResponseFactory.forbidden("Current user is not an administrator of the application's provider organization");
      }
    }

    // XXX: don't send the secrets over the wire
    application.setInstantiation_secret(null);

    // TODO: send back the link to the MarketBuyEndpoint
    return Response.ok(application).build();
  }
}
