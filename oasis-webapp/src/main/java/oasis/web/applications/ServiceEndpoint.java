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

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.DuplicateKeyException;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.usecases.DeleteService;
import oasis.usecases.ServiceValidator;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/service/{service_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
public class ServiceEndpoint {
  @Inject ServiceRepository serviceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject EtagService etagService;
  @Inject DeleteService deleteService;
  @Inject ServiceValidator serviceValidator;

  @Context SecurityContext securityContext;

  @PathParam("service_id") String serviceId;

  @GET
  public Response getService() {
    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return ResponseFactory.notFound("Service not found");
    }

    final boolean isPortal;
    if (!service.isVisible()) {
      // only the admins or designated users should be able to see the service if it's "hidden"
      // and only through the "portal" app
      OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      if (principal == null) {
        return OAuthAuthenticationFilter.challengeResponse();
      }
      AccessToken accessToken = principal.getAccessToken();
      isPortal = accessToken.isPortal();
      if (!isPortal) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      String userId = accessToken.getAccountId();
      boolean isAppUser = accessControlRepository.getAccessControlEntry(service.getInstance_id(), userId) != null;
      if (!isAppUser && !isAppAdmin(userId, service)) {
        return ResponseFactory.forbidden("Current user is neither an app_admin or app_user for the service");
      }
    } else {
      isPortal = false;
    }

    // XXX: don't send secrets over the wire
    service.setSubscription_secret(null);
    // XXX: hide 'portals' to non-portal clients
    if (!isPortal) {
      service.setPortals(null);
    }

    return Response.ok()
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @PUT
  @Authenticated
  @Portal
  public Response update(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      Service service
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
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

    @Nullable String error = serviceValidator.validateService(service, updatedService.getInstance_id());
    if (error != null) {
      return ResponseFactory.unprocessableEntity(error);
    }

    // Make sure that the status will not be changed after the update
    service.setStatus(updatedService.getStatus());

    try {
      service = serviceRepository.updateService(service, etagService.parseEtag(ifMatch));
    } catch (DuplicateKeyException e) {
      return Response.status(Response.Status.CONFLICT).build();
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
  @Authenticated
  @Portal
  public Response delete(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
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

  @POST
  @Path("/portals")
  @Portal
  public Response addPortal(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      AddPortalRequest request
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!ClientIds.PORTAL.equals(accessToken.getServiceProviderId()) && !accessToken.getServiceProviderId().equals(request.portalId)) {
      return ResponseFactory.forbidden("Only the canonical portal can add other portals");
    }

    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AppInstance portal = appInstanceRepository.getAppInstance(request.portalId);
    if (portal == null) {
      return ResponseFactory.unprocessableEntity("Unknown app instance: " + request.portalId);
    }
    if (!portal.isPortal()) {
      return ResponseFactory.unprocessableEntity("Not a portal: " + request.portalId);
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), portal)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the portal");
    }

    try {
      serviceRepository.addPortal(serviceId, request.portalId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    return ResponseFactory.NO_CONTENT;
  }

  @DELETE
  @Path("/portals/{portalId}")
  @Portal
  public Response removePortal(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      @PathParam("portalId") String portalId
  ) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!ClientIds.PORTAL.equals(accessToken.getServiceProviderId()) && !accessToken.getServiceProviderId().equals(portalId)) {
      return ResponseFactory.forbidden("Only the canonical portal can remove other portals");
    }

    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AppInstance portal = appInstanceRepository.getAppInstance(portalId);
    if (portal == null || !portal.isPortal()) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), portal)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the portal");
    }

    try {
      serviceRepository.removePortal(serviceId, portalId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    return ResponseFactory.NO_CONTENT;
  }

  public static class AddPortalRequest {
    @JsonProperty
    String portalId;
  }
}
