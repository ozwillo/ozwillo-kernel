package oasis.web.applications;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.usecases.DeleteAppInstance;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "app-instances", description = "Application instances")
public class AppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject ServiceRepository serviceRepository;
  @Inject EtagService etagService;
  @Inject DeleteAppInstance deleteAppInstance;

  @Context SecurityContext securityContext;

  @PathParam("instance_id")
  String instanceId;

  @GET
  @ApiOperation(
      value = "Retrieve information on an application instance",
      response = AppInstance.class
  )
  public Response getAppInstance() {
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.notFound("Application instance not found");
    }

    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    // XXX: Don't send the secrets over the wire
    instance.setDestruction_secret(null);
    // XXX: keep the redirect_uri_validation_disabled "secret"
    instance.unsetRedirect_uri_validation_disabled();

    return Response.ok()
        .tag(etagService.getEtag(instance))
        .entity(instance)
        .build();
  }

  @GET
  @Path("/services")
  @ApiOperation(
      value = "Retrieve the services of the application instance",
      response = Service.class,
      responseContainer = "Array"
  )
  public Response getServices() {
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

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
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    if (service.getInstance_id() != null && !service.getInstance_id().equals(instanceId)) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    service.setInstance_id(instanceId);
    service.setProvider_id(instance.getProvider_id());
    service = serviceRepository.createService(service);
    if (service == null) {
      if (appInstanceRepository.getAppInstance(instanceId) == null) {
        return ResponseFactory.NOT_FOUND;
      }
      // Must then be a duplicate key of some sort
      return Response.status(Response.Status.CONFLICT).build();
    }
    URI created = Resteasy1099.getBaseUriBuilder(uriInfo).path(ServiceEndpoint.class).build(service.getId());
    return Response.created(created)
        .contentLocation(created)
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @DELETE
  @ApiOperation("Detroys the application instance")
  public Response destroy(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    DeleteAppInstance.Request request = new DeleteAppInstance.Request(instanceId);
    request.callProvider = true;
    request.checkStatus = Optional.absent();
    request.checkVersions = Optional.of(etagService.parseEtag(ifMatch));
    DeleteAppInstance.Status status = deleteAppInstance.deleteInstance(request, new DeleteAppInstance.Stats());

    switch (status) {
      case PROVIDER_CALL_ERROR:
      case PROVIDER_STATUS_ERROR:
        return Response.status(Response.Status.BAD_GATEWAY).build();
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
        // race condition?
        return ResponseFactory.NOT_FOUND;
      case DELETED_INSTANCE:
        return Response.ok().build();
      default:
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
