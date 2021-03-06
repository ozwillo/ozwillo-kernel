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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/subscription/{subscription_id}")
@Authenticated @OAuth
@Portal
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("subscription_id") String subscriptionId;

  @GET
  public Response get() {
    UserSubscription subscription = userSubscriptionRepository.getUserSubscription(subscriptionId);
    if (subscription == null) {
      return ResponseFactory.NOT_FOUND;
    }

    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!userId.equals(subscription.getUser_id())) {
      AppInstance appInstance = getAppInstance(subscription.getService_id());
      if (appInstance == null) {
        return ResponseFactory.NOT_FOUND;
      }
      if (!appAdminHelper.isAdmin(userId, appInstance)) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
    }

    return Response.ok()
        .tag(etagService.getEtag(subscription))
        .entity(subscription)
        .build();
  }

  @DELETE
  public Response unsubscribe(@HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    UserSubscription subscription = userSubscriptionRepository.getUserSubscription(subscriptionId);
    if (subscription == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    switch (subscription.getSubscription_type()) {
      case PERSONAL:
        // Personal subscriptions can only be deleted by the user
        if (!userId.equals(subscription.getUser_id())) {
          return Response.status(Response.Status.FORBIDDEN).build();
        }
        break;
      case ORGANIZATION: {
        // Organization subscriptions can only be deleted by an app-admin
        AppInstance appInstance = getAppInstance(subscription.getService_id());
        if (appInstance == null) {
          return ResponseFactory.NOT_FOUND;
        }
        if (!appAdminHelper.isAdmin(userId, appInstance)) {
          return Response.status(Response.Status.FORBIDDEN).build();
        }
        break;
      }
      default:
        return Response.serverError().build();
    }


    boolean deleted;
    try {
      deleted = userSubscriptionRepository.deleteUserSubscription(subscriptionId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.notFound("The subscription does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }

  private AppInstance getAppInstance(String serviceId) {
    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return null;
    }
    return appInstanceRepository.getAppInstance(service.getInstance_id());
  }
}
