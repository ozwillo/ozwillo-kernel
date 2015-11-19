/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authz.Scopes;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.utils.ResponseFactory;

@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
@Path("/apps/instance/organization/{organization_id}")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationAppInstanceEndpoint {
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  public Response get() {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(oAuthUserId, organizationId);
    if (organizationMembership == null) {
      return ResponseFactory.forbidden("Current user is not a member of the organization");
    }
    if (!organizationMembership.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an administrator of target organization");
    }

    Iterable<AppInstance> appInstances = appInstanceRepository.findByOrganizationId(organizationId);
    return Response.ok()
        .entity(new GenericEntity<Iterable<AppInstance>>(
            Iterables.transform(appInstances,
                new Function<AppInstance, AppInstance>() {
                  @Override
                  public AppInstance apply(AppInstance instance) {
                    // XXX: Don't send secrets over the wire
                    instance.setDestruction_secret(null);
                    instance.setStatus_changed_secret(null);
                    // XXX: keep the redirect_uri_validation_disabled "secret"
                    instance.unsetRedirect_uri_validation_disabled();
                    return instance;
                  }
                })) {})
        .build();
  }
}
