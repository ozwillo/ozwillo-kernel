package oasis.web.applications;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance.NeededScope;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/pending-instance/{instance_id}")
@Authenticated @Client
@Api(value = "instance-registration", description = "Application Factories' callback")
public class InstanceRegistrationEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject ScopeRepository scopeRepository;

  @PathParam("instance_id") String instanceId;

  @Context SecurityContext securityContext;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Acknowledges the provisioning of an instance",
      notes = "See the <a href='https://docs.google.com/document/d/1V0lmEPTVl_UH7Dl-6AsiedALviJvjHW7RGw5jYg0Ah8/edit?usp=sharing'>Application Provisioning Protocol</a>"
  )
  public Response instantiated(
      @Context UriInfo uriInfo,
      AcknowledgementRequest acknowledgementRequest) {
    if (!((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(instanceId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (!instanceId.equals(acknowledgementRequest.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    // TODO: check uniqueness of service IDs, scope IDs and needed scope IDs
    // TODO: check that service's and scope's instance_id is exact.
    // TODO: check existence of needed scopes.

    if (!appInstanceRepository.instantiated(instanceId, acknowledgementRequest.getNeeded_scopes())) {
      return ResponseFactory.notFound("Pending instance not found");
    }

    for (Scope scope : acknowledgementRequest.getScopes()) {
      scope.setInstance_id(instanceId);
      scope.computeId();
      scopeRepository.createOrUpdateScope(scope);
    }
    Map<String, String> acknowledgementResponse = new LinkedHashMap<>(acknowledgementRequest.getServices().size());
    for (Service service : acknowledgementRequest.getServices()) {
      service.setInstance_id(instanceId);
      service = serviceRepository.createService(service);
      acknowledgementResponse.put(service.getLocal_id(), service.getId());
    }
    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(AppInstanceEndpoint.class).build(instanceId))
        .entity(new GenericEntity<Map<String, String>>(acknowledgementResponse) {})
        .build();
  }

  @DELETE
  @ApiOperation(
      value = "Notifies an error while provisioning the instance",
      notes = "See the <a href='https://docs.google.com/document/d/1V0lmEPTVl_UH7Dl-6AsiedALviJvjHW7RGw5jYg0Ah8/edit?usp=sharing'>Application Provisioning Protocol</a>"
  )
  public Response errorInstantiating() {
    if (((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(instanceId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (!appInstanceRepository.deletePendingInstance(instanceId)) {
      return ResponseFactory.notFound("Pending instance not found");
    }
    return Response.ok().build();
  }

  public static class AcknowledgementRequest {
    @JsonProperty String instance_id;
    @JsonProperty List<Service> services;
    @JsonProperty List<Scope> scopes;
    @JsonProperty List<NeededScope> needed_scopes;

    public String getInstance_id() {
      return instance_id;
    }

    public List<Service> getServices() {
      if (services == null) {
        services = new ArrayList<>();
      }
      return services;
    }

    public List<Scope> getScopes() {
      if (scopes == null) {
        scopes = new ArrayList<>();
      }
      return scopes;
    }

    public List<NeededScope> getNeeded_scopes() {
      if (needed_scopes == null) {
        needed_scopes = new ArrayList<>();
      }
      return needed_scopes;
    }
  }
}
