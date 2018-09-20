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

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
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

@Path("/apps/subscriptions/service/{service_id}")
@Authenticated @OAuth
@Portal
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServiceSubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("service_id") String serviceId;

  @GET
  public Response getSubscriptions() {
    AppInstance instance = getAppInstance();
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }

    Stream<UserSubscription> subscriptions = Streams.stream(userSubscriptionRepository.getSubscriptionsForService(serviceId));

    // Filter the list to only the app_users and app_admins.
    final ImmutableSet<String> app_users_and_admins = Stream.concat(
        Streams.stream(accessControlRepository.getAccessControlListForAppInstance(instance.getId()))
            .map(AccessControlEntry::getUser_id),
        appAdminHelper.getAdmins(instance)
    ).collect(ImmutableSet.toImmutableSet());
    subscriptions = subscriptions
        .filter(input -> app_users_and_admins.contains(input.getUser_id()));

    return Response.ok()
        .entity(new GenericEntity<Stream<ServiceSub>>(subscriptions.map(
            input -> {
              ServiceSub sub = new ServiceSub();
              sub.id = input.getId();
              sub.subscription_uri = uriInfo.getBaseUriBuilder().path(SubscriptionEndpoint.class).build(input.getId()).toString();
              sub.subscription_etag = etagService.getEtag(input).toString();
              sub.user_id = input.getUser_id();
              final UserAccount user = accountRepository.getUserAccountById(input.getUser_id());
              sub.user_name = user == null ? null : user.getDisplayName();
              sub.subscription_type = input.getSubscription_type();
              sub.creator_id = MoreObjects.firstNonNull(input.getCreator_id(), input.getUser_id());
              // TODO: check access rights to the user name
              // TODO: introduce some caching (it's not unlikely many subscriptions will have the same creator)
              final UserAccount creator = accountRepository.getUserAccountById(sub.creator_id);
              sub.creator_name = creator == null ? null : creator.getDisplayName();
              return sub;
            })) {})
        .build();
  }

  @POST
  public Response subscribe(UserSubscription subscription) {
    if (subscription.getService_id() != null && !serviceId.equals(subscription.getService_id())) {
      return ResponseFactory.unprocessableEntity("service_id doesn't match URL");
    }
    subscription.setService_id(serviceId);

    if (subscription.getSubscription_type() != null && subscription.getSubscription_type() != UserSubscription.SubscriptionType.ORGANIZATION) {
      return ResponseFactory.unprocessableEntity("This endpoint can only create non-personal subscriptions");
    }
    subscription.setSubscription_type(UserSubscription.SubscriptionType.ORGANIZATION);
    AppInstance instance = getAppInstance();
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (Strings.isNullOrEmpty(instance.getProvider_id())) {
      return ResponseFactory.forbidden("Cannot create a non-personal subscription for a personal app instance");
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }
    if (accessControlRepository.getAccessControlEntry(instance.getId(), subscription.getUser_id()) == null
        && !appAdminHelper.isAdmin(subscription.getUser_id(), instance)) {
      return ResponseFactory.unprocessableEntity("Target user is neither an app_admin or app_user for the service");
    }
    return createSubscription(subscription);
  }

  private AppInstance getAppInstance() {
    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return null;
    }
    return appInstanceRepository.getAppInstance(service.getInstance_id());
  }

  private Response createSubscription(UserSubscription subscription) {
    subscription.setCreator_id(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    subscription = userSubscriptionRepository.createUserSubscription(subscription);
    if (subscription == null) {
      return ResponseFactory.conflict("Subscription for that user and service already exists");
    }
    return Response.created(uriInfo.getBaseUriBuilder().path(SubscriptionEndpoint.class).build(subscription.getId()))
        .tag(etagService.getEtag(subscription))
        .entity(subscription)
        .build();
  }

  static class ServiceSub {
    @JsonProperty String id;
    @JsonProperty String subscription_uri;
    @JsonProperty String subscription_etag;
    @JsonProperty String user_id;
    @JsonProperty String user_name;
    @JsonProperty UserSubscription.SubscriptionType subscription_type;
    @JsonProperty String creator_id;
    @JsonProperty String creator_name;
  }
}
