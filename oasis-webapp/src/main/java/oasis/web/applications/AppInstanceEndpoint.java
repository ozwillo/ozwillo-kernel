package oasis.web.applications;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/apps/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "app-instances", description = "Application instances")
public class AppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;

  @PathParam("instance_id")
  String instanceId;

  @GET
  @ApiOperation(
      value = "Retrieve information on an application instance",
      response = AppInstance.class
  )
  public Response getAppInstance() {
    // TODO: only the instance admins should be able to call that API
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.notFound("Application instance not found");
    }
    return Response.ok(instance).build();
  }

  @GET
  @Path("/services")
  @ApiOperation(
      value = "Retrieve the services of the application instance",
      response = Service.class,
      responseContainer = "Array"
  )
  public Response getServices() {
    Iterable<Service> services = serviceRepository.getServicesOfInstance(instanceId);
    // TODO: check that the instance exists and return a 404 otherwise
    return Response.ok(new GenericEntity<List<Service>>(
        FluentIterable.from(services)
            .transform(new Function<Service, Service>() {
              @Override
              public Service apply(Service input) {
                // XXX: don't send secrets over the wire
                input.setSubscription_secret(null);
                return input;
              }
            })
            .toList()
    ) {})
        .build();
  }
}
