package oasis.web.applications;

import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.urls.Urls;
import oasis.usecases.ChangeAppInstanceStatus;
import oasis.usecases.ImmutableChangeAppInstanceStatus;
import oasis.usecases.ServiceValidator;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "app-instances", description = "Application instances")
public class AppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;
  @Inject Provider<ServiceValidator> serviceValidatorProvider;
  @Inject ChangeAppInstanceStatus changeAppInstanceStatus;

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

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      return ResponseFactory.forbidden("Cannot read information about another instance");
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    // XXX: Don't send the secrets over the wire
    instance.setDestruction_secret(null);
    instance.setStatus_changed_secret(null);
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

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      return ResponseFactory.forbidden("Cannot list services of another instance");
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), instance)) {
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

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      return ResponseFactory.forbidden("Cannot create service in another instance");
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    @Nullable String error = serviceValidatorProvider.get().validateService(service, instanceId);
    if (error != null) {
      return ResponseFactory.unprocessableEntity(error);
    }

    service.setInstance_id(instanceId);
    service.setProvider_id(instance.getProvider_id());
    // The instance could be STOPPED
    Service.Status serviceStatus = Service.Status.forAppInstanceStatus(instance.getStatus());
    service.setStatus(serviceStatus);
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

  @POST
  @ApiOperation("Change the status of the application instance")
  @WithScopes(Scopes.PORTAL)
  public Response changeStatus(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
      ModifyStatusRequest request
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    Response error = request.checkStatus();
    if (error != null) {
      return error;
    }
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }
    if (!Strings.isNullOrEmpty(instance.getProvider_id())) {
      Organization organization = directoryRepository.getOrganization(instance.getProvider_id());
      if (organization == null || organization.getStatus() == Organization.Status.DELETED) {
        return ResponseFactory.forbidden("Can't change the status of an app instance related to a deleted organization.");
      }
    }

    ChangeAppInstanceStatus.Request changeAppInstanceStatusRequest = ImmutableChangeAppInstanceStatus.Request.builder()
        .appInstance(instance)
        .requesterId(userId)
        .newStatus(request.status)
        .ifMatch(etagService.parseEtag(ifMatch))
        .notifyAdmins(true)
        .build();
    ChangeAppInstanceStatus.Response changeAppInstanceStatusResponse = changeAppInstanceStatus.updateStatus(changeAppInstanceStatusRequest);

    switch (changeAppInstanceStatusResponse.responseStatus()) {
      case SUCCESS:
      case NOTHING_TO_MODIFY:
        return Response.ok(changeAppInstanceStatusResponse.appInstance())
            .tag(etagService.getEtag(changeAppInstanceStatusResponse.appInstance()))
            .build();
      case VERSION_CONFLICT:
        return ResponseFactory.preconditionFailed("Invalid version for app-instance " + instanceId);
      case NOT_FOUND:
        return ResponseFactory.NOT_FOUND;
      default:
        return Response.serverError().build();
    }
  }

  public static class ModifyStatusRequest {
    @JsonProperty AppInstance.InstantiationStatus status;

    @Nullable protected Response checkStatus() {
      if (status == null) {
        return ResponseFactory.unprocessableEntity("Instantiation status must be provided.");
      }
      switch (status) {
        case RUNNING:
        case STOPPED:
          break;
        default:
          return ResponseFactory.unprocessableEntity("Instantiation status not allowed: " + status.name());
      }
      return null;
    }
  }
}
