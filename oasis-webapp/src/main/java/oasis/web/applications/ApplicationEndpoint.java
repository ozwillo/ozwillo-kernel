package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.Application;
import oasis.services.applications.ApplicationService;
import oasis.web.utils.ResponseFactory;

@Path("/apps/app/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "apps", description = "Applications")
public class ApplicationEndpoint {
  @Inject ApplicationService applicationService;

  @PathParam("application_id") String applicationId;

  @GET
  @ApiOperation(
      value = "Get information about an application",
      response = Application.class
  )
  public Response getApplication() {
    // TODO: only the application admins should be able to see it if it's "hidden"
    Application application = applicationService.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application not found");
    }

    // XXX: don't send the secrets over the wire
    application.setInstantiation_secret(null);

    // TODO: send back the link to the MarketBuyEndpoint
    return Response.ok(application).build();
  }
}
