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

import oasis.model.applications.v2.Service;
import oasis.services.applications.ServiceService;
import oasis.web.utils.ResponseFactory;

@Path("/apps/service/{service_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "services", description = "Application services")
public class ServiceEndpoint {
  @Inject ServiceService serviceService;

  @PathParam("service_id") String serviceId;

  @GET
  @ApiOperation(
      value = "Retrieves information about a service",
      response = Service.class
  )
  public Response getService() {
    Service service = serviceService.getService(serviceId);
    if (service == null) {
      return ResponseFactory.notFound("Service not found");
    }

    // XXX: don't send secrets over the wire
    service.setSubscription_secret(null);

    return Response.ok(service).build();
  }
}
