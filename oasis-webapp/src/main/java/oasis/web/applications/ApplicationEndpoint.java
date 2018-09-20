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

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.authn.AccessToken;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/app/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
public class ApplicationEndpoint {
  @Inject ApplicationRepository applicationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Context SecurityContext securityContext;
  @PathParam("application_id") String applicationId;

  @GET
  public Response getApplication() {
    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application not found");
    }

    if (!application.isVisible()) {
      // only the application admins should be able to see it if it's "hidden"
      // and only through the "portal" app
      OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      if (principal == null) {
        return OAuthAuthenticationFilter.challengeResponse();
      }
      AccessToken accessToken = principal.getAccessToken();
      if (!accessToken.isPortal()) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(accessToken.getAccountId(), application.getProvider_id());
      if (organizationMembership == null) {
        return ResponseFactory.forbidden("Current user is not a member of the application's provider organization");
      }
      if (!organizationMembership.isAdmin()) {
        return ResponseFactory.forbidden("Current user is not an administrator of the application's provider organization");
      }
    }

    // XXX: don't send the secrets over the wire
    application.setInstantiation_secret(null);
    application.setCancellation_secret(null);

    // TODO: send back the link to the MarketBuyEndpoint
    return Response.ok(application).build();
  }
}
