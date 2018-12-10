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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/app/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
public class ApplicationEndpoint {
  @Inject ApplicationRepository applicationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject Provider<AppInstanceRepository> appInstanceRepository;
  @Inject Provider<AppAdminHelper> appAdminHelper;
  @Inject Provider<EtagService> etagService;

  @Context SecurityContext securityContext;
  @PathParam("application_id") String applicationId;

  @GET
  public Response getApplication() {
    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application not found");
    }

    final boolean isPortal;
    if (!application.isVisible()) {
      // only the application admins should be able to see it if it's "hidden"
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
      OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(accessToken.getAccountId(), application.getProvider_id());
      if (organizationMembership == null) {
        return ResponseFactory.forbidden("Current user is not a member of the application's provider organization");
      }
      if (!organizationMembership.isAdmin()) {
        return ResponseFactory.forbidden("Current user is not an administrator of the application's provider organization");
      }
    } else {
      isPortal = false;
    }

    // XXX: don't send the secrets over the wire
    application.setInstantiation_secret(null);
    application.setCancellation_secret(null);

    // XXX: hide 'portals' to non-portal clients
    if (!isPortal) {
      application.setPortals(null);
    }

    // TODO: send back the link to the MarketBuyEndpoint
    return Response.ok(application).build();
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

    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!application.isVisible()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    AppInstance portal = appInstanceRepository.get().getAppInstance(request.portalId);
    if (portal == null) {
      return ResponseFactory.unprocessableEntity("Unknown app instance: " + request.portalId);
    }
    if (!portal.isPortal()) {
      return ResponseFactory.unprocessableEntity("Not a portal: " + request.portalId);
    }
    if (!appAdminHelper.get().isAdmin(accessToken.getAccountId(), portal)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the portal");
    }

    try {
      applicationRepository.addPortal(applicationId, request.portalId, etagService.get().parseEtag(ifMatch));
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

    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!application.isVisible()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    AppInstance portal = appInstanceRepository.get().getAppInstance(portalId);
    if (portal == null || !portal.isPortal()) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!appAdminHelper.get().isAdmin(accessToken.getAccountId(), portal)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the portal");
    }

    try {
      applicationRepository.removePortal(applicationId, portalId, etagService.get().parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    return ResponseFactory.NO_CONTENT;
  }

  public static class AddPortalRequest {
    @JsonProperty String portalId;
  }
}
