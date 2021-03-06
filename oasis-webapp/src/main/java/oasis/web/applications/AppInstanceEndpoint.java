/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.applications;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.usecases.ChangeAppInstanceStatus;
import oasis.usecases.DeleteAppInstance;
import oasis.usecases.ImmutableChangeAppInstanceStatus;
import oasis.usecases.ImmutableDeleteAppInstance;
import oasis.usecases.ServiceValidator;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class AppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;
  @Inject Provider<ServiceValidator> serviceValidatorProvider;
  @Inject Provider<ChangeAppInstanceStatus> changeAppInstanceStatus;
  @Inject Provider<DeleteAppInstance> deleteAppInstance;

  @Context SecurityContext securityContext;

  @PathParam("instance_id")
  String instanceId;

  @GET
  public Response getAppInstance() {
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.notFound("Application instance not found");
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !accessToken.isPortal()) {
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

    // XXX: hide the portal to non-portal clients
    if (!accessToken.isPortal()) {
      instance.setPortal_id(null);
    }

    return Response.ok()
        .tag(etagService.getEtag(instance))
        .entity(instance)
        .build();
  }

  @GET
  @Path("/services")
  public Response getServices() {
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    final boolean isPortal = accessToken.isPortal();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !isPortal) {
      return ResponseFactory.forbidden("Cannot list services of another instance");
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the instance");
    }

    Iterable<Service> services = serviceRepository.getServicesOfInstance(instanceId);
    return Response.ok()
        .entity(new GenericEntity<Stream<Service>>(Streams.stream(services)
            .map(input -> {
              // XXX: don't send secrets over the wire
              input.setSubscription_secret(null);
              // XXX: hide 'portals' to non-portal clients
              if (!isPortal) {
                input.setPortals(null);
              }
              return input;
            })) {})
        .build();
  }

  @POST
  @Path("/services")
  public Response addService(@Context UriInfo uriInfo, Service service) {
    AppInstance instance = appInstanceRepository.getAppInstance(instanceId);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instanceId.equals(accessToken.getServiceProviderId()) && !accessToken.isPortal()) {
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
    service.setPortals(Sets.newHashSet(instance.getPortal_id()));
    service = serviceRepository.createService(service);
    if (service == null) {
      if (appInstanceRepository.getAppInstance(instanceId) == null) {
        return ResponseFactory.NOT_FOUND;
      }
      // Must then be a duplicate key of some sort
      return Response.status(Response.Status.CONFLICT).build();
    }
    URI created = uriInfo.getBaseUriBuilder().path(ServiceEndpoint.class).build(service.getId());
    return Response.created(created)
        .contentLocation(created)
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @POST
  @Portal
  public Response changeStatus(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      ModifyStatusRequest request
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
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
    ChangeAppInstanceStatus.Response changeAppInstanceStatusResponse = changeAppInstanceStatus.get().updateStatus(changeAppInstanceStatusRequest);

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

  @DELETE
  @Portal
  public Response deleteInstance(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    DeleteAppInstance.Request request = ImmutableDeleteAppInstance.Request.builder()
        .instanceId(instanceId)
        .callProvider(true)
        .checkStatus(AppInstance.InstantiationStatus.PENDING)
        .checkVersions(etagService.parseEtag(ifMatch))
        .notifyAdmins(false)
        .build();
    DeleteAppInstance.Status status = deleteAppInstance.get().deleteInstance(request, new DeleteAppInstance.Stats());
    switch (status) {
      case BAD_INSTANCE_VERSION:
        return ResponseFactory.preconditionFailed("Invalid version for app-instance " + instanceId);
      case BAD_INSTANCE_STATUS:
        return ResponseFactory.forbidden("Cannot delete a running or stopped instance");
      case PROVIDER_CALL_ERROR:
      case PROVIDER_STATUS_ERROR:
        return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
      case DELETED_INSTANCE:
        return ResponseFactory.NO_CONTENT;
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
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
