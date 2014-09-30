package oasis.web.applications;

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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.resteasy.Resteasy1099;
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

    // XXX: Don't send the secrets over the wire
    instance.setDestruction_secret(null);

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
    // TODO: only the instance admins should be able to call that API
    Iterable<Service> services = serviceRepository.getServicesOfInstance(instanceId);
    // TODO: check that the instance exists and return a 404 otherwise
    return Response.ok()
        .entity(new GenericEntity<Iterable<Service>>(Iterables.transform(services,
            new Function<Service, Service>() {
              @Override
              public Service apply(Service input) {
                // XXX: don't send secrets over the wire
                input.setSubscription_secret(null);
                return input;
              }
            })) {})
        .build();
  }

  @POST
  @Path("/services")
  @ApiOperation(
      value = "Adds a new service to the application instance",
      response = Service.class
  )
  public Response addService(@Context UriInfo uriInfo, Service service) {
    // TODO: only the instance admins should be able to call that API
    if (service.getInstance_id() != null && !service.getInstance_id().equals(instanceId)) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    service.setInstance_id(instanceId);
    service = serviceRepository.createService(service);
    if (service == null) {
      if (appInstanceRepository.getAppInstance(instanceId) == null) {
        return ResponseFactory.NOT_FOUND;
      }
      // Must then be a duplicate key of some sort
      return Response.status(Response.Status.CONFLICT).build();
    }
    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(ServiceEndpoint.class).build(service.getId()))
        .entity(service)
        .build();
  }
}
