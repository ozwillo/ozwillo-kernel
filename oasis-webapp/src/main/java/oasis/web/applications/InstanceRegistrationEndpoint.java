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
import com.google.common.base.Optional;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstance.NeededScope;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.usecases.DeleteAppInstance;
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
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject DeleteAppInstance deleteAppInstance;

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

    AppInstance instance = appInstanceRepository.instantiated(instanceId, acknowledgementRequest.getNeeded_scopes(),
        acknowledgementRequest.destruction_uri, acknowledgementRequest.destruction_secret);
    if (instance == null) {
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

      // Automatically subscribe the "instantiator" to all services
      UserSubscription subscription = new UserSubscription();
      subscription.setUser_id(instance.getInstantiator_id());
      subscription.setService_id(service.getId());
      subscription.setCreator_id(instance.getInstantiator_id());
      subscription.setSubscription_type(
          instance.getProvider_id() == null
              ? UserSubscription.SubscriptionType.PERSONAL
              : UserSubscription.SubscriptionType.ORGANIZATION);
      userSubscriptionRepository.createUserSubscription(subscription);
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
    if (!((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(instanceId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    DeleteAppInstance.Request request = new DeleteAppInstance.Request(instanceId);
    request.callProvider = false;
    request.checkStatus = Optional.of(AppInstance.InstantiationStatus.PENDING);
    DeleteAppInstance.Status status = deleteAppInstance.deleteInstance(request, new DeleteAppInstance.Stats());

    switch (status) {
      case BAD_INSTANCE_STATUS:
        // Instance has already been provisioned
        return ResponseFactory.NOT_FOUND;
      case DELETED_INSTANCE:
      // below are race-condition: we couldn't have authenticated the client otherwise
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
        return Response.ok().build();
      default:
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  public static class AcknowledgementRequest {
    @JsonProperty String instance_id;
    @JsonProperty List<Service> services;
    @JsonProperty List<Scope> scopes;
    @JsonProperty List<NeededScope> needed_scopes;
    @JsonProperty String destruction_uri;
    @JsonProperty String destruction_secret;

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
