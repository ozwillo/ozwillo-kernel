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

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Service;
import oasis.services.applications.AppInstanceService;
import oasis.services.applications.ServiceService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/apps/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class AppInstanceEndpoint {
  @Inject AppInstanceService appInstanceService;
  @Inject ServiceService serviceService;

  @PathParam("instance_id")
  String instanceId;

  @GET
  public Response getAppInstance() {
    // TODO: only the instance admins should be able to call that API
    AppInstance instance = appInstanceService.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.notFound("Application instance not found");
    }
    return Response.ok(instance).build();
  }

  @GET
  @Path("/services")
  public Response getServices() {
    Iterable<Service> services = serviceService.getServicesOfInstance(instanceId);
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
