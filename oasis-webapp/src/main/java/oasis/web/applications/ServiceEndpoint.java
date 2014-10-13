package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.usecases.DeleteService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/service/{service_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
@Api(value = "services", description = "Application services")
public class ServiceEndpoint {
  @Inject ServiceRepository serviceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject EtagService etagService;
  @Inject DeleteService deleteService;

  @Context SecurityContext securityContext;

  @PathParam("service_id") String serviceId;

  @GET
  @ApiOperation(
      value = "Retrieves information about a service",
      response = Service.class
  )
  public Response getService() {
    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return ResponseFactory.notFound("Service not found");
    }

    if (!service.isVisible()) {
      // only the admins or designated users should be able to see the service if it's "hidden"
      OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      if (principal == null) {
        return OAuthAuthenticationFilter.challengeResponse();
      }
      String userId = principal.getAccessToken().getAccountId();
      boolean isAppUser = accessControlRepository.getAccessControlEntry(service.getInstance_id(), userId) != null;
      if (!isAppUser && !isAppAdmin(userId, service)) {
        return ResponseFactory.forbidden("Current user is neither an app_admin or app_user for the service");
      }
    }

    // XXX: don't send secrets over the wire
    service.setSubscription_secret(null);

    return Response.ok()
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @PUT
  @ApiOperation(
      value = "Updates the service",
      response = Service.class
  )
  @Authenticated
  public Response update(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
      Service service
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }
    if (service.getId() != null && !service.getId().equals(serviceId)) {
      return ResponseFactory.unprocessableEntity("id doesn't match the URL");
    }
    Service updatedService = serviceRepository.getService(serviceId);
    if (updatedService == null) {
      return ResponseFactory.NOT_FOUND;
    }

    if (!isAppAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), updatedService)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }

    try {
      service = serviceRepository.updateService(service, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (service == null) {
      // deleted in between?
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok()
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @DELETE
  @ApiOperation("Deletes the service")
  @Authenticated
  public Response delete(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    Service serviceToBeDeleted = serviceRepository.getService(serviceId);
    if (serviceToBeDeleted == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!isAppAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), serviceToBeDeleted)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }

    DeleteService.Status status = deleteService.deleteService(serviceId, etagService.parseEtag(ifMatch));
    switch (status) {
      case BAD_SERVICE_VERSION:
        return Response.status(Response.Status.PRECONDITION_FAILED).build();
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
        return ResponseFactory.NOT_FOUND;
      case DELETED_SERVICE:
        return ResponseFactory.NO_CONTENT;
      default:
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private boolean isAppAdmin(String userId, Service service) {
    return appAdminHelper.isAdmin(userId, appInstanceRepository.getAppInstance(service.getInstance_id()));
  }
}
