package oasis.web.applications;

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
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.i18n.LocalizableString;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/user/{user_id}")
@Authenticated @OAuth
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "subs-user", description = "User-Service subscriptions (from the user point of view)")
public class UserSubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("user_id") String userId;

  @GET
  @ApiOperation(
      value = "Retrieves services to which the user is subscribed",
      response = UserSub.class,
      responseContainer = "Array"
  )
  public Response getSubscriptions() {
    if (!userId.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return ResponseFactory.forbidden("Cannot list subscriptions for another user");
    }

    Iterable<UserSubscription> subscriptions = userSubscriptionRepository.getUserSubscriptions(userId);
    return Response.ok()
        .entity(new GenericEntity<Iterable<UserSub>>(Iterables.transform(subscriptions,
            new Function<UserSubscription, UserSub>() {
              @Override
              public UserSub apply(UserSubscription input) {
                UserSub sub = new UserSub();
                sub.id = input.getId();
                sub.subscription_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(SubscriptionEndpoint.class).build(input.getId()).toString();
                sub.subscription_etag = etagService.getEtag(input);
                sub.service_id = input.getService_id();
                final Service service = serviceRepository.getService(input.getService_id());
                sub.service_name = service == null ? null : service.getName();
                sub.subscription_type = input.getSubscription_type();
                sub.creator_id = MoreObjects.firstNonNull(input.getCreator_id(), input.getUser_id());
                // TODO: check access rights to the user name
                final UserAccount creator = accountRepository.getUserAccountById(sub.creator_id);
                sub.creator_name = creator == null ? null : creator.getDisplayName();
                return sub;
              }
            })) {})
        .build();
  }

  @POST
  @ApiOperation(
      value = "Subscribes the user to a service",
      response = UserSubscription.class
  )
  public Response subscribe(UserSubscription subscription) {
    if (subscription.getUser_id() != null && !userId.equals(subscription.getUser_id())) {
      return ResponseFactory.unprocessableEntity("user_id doesn't match URL");
    }
    subscription.setUser_id(userId);
    if (subscription.getSubscription_type() != UserSubscription.SubscriptionType.ORGANIZATION) {
      return subscribePersonal(subscription);
    } else {
      return subscribeOrganization(subscription);
    }
  }

  /** Called when the subscription_type is NOT ORGANIZATION. */
  private Response subscribePersonal(UserSubscription subscription) {
    subscription.setSubscription_type(UserSubscription.SubscriptionType.PERSONAL);
    if (!userId.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return ResponseFactory.forbidden("Cannot create a personal subscription for another user");
    }
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    // a personal subscription can only target a public service, or one for which the user is an app_user
    if (!service.isVisible()) {
      AppInstance instance = appInstanceRepository.getAppInstance(service.getInstance_id());
      if (instance == null) {
        return ResponseFactory.build(Response.Status.INTERNAL_SERVER_ERROR, "Oops, service has no app instance");
      }
      if (accessControlRepository.getAccessControlEntry(instance.getId(), userId) == null
          && !appAdminHelper.isAdmin(userId, instance)) {
        return ResponseFactory.forbidden("Cannot subscribe to this service");
      }
    }
    return createSubscription(subscription);
  }

  /** Called when the subscription_type is ORGANIZATION. */
  private Response subscribeOrganization(UserSubscription subscription) {
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    AppInstance instance = appInstanceRepository.getAppInstance(service.getInstance_id());
    if (instance == null) {
      return ResponseFactory.build(Response.Status.INTERNAL_SERVER_ERROR, "Oops, service has no app instance");
    }
    if (Strings.isNullOrEmpty(instance.getProvider_id())) {
      return ResponseFactory.forbidden("Cannot create a non-personal subscription for a personal app instance");
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }
    if (accessControlRepository.getAccessControlEntry(instance.getId(), userId) == null
        && !appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.unprocessableEntity("Target user is neither an app_admin or app_user for the service");
    }
    return createSubscription(subscription);
  }

  private Response createSubscription(UserSubscription subscription) {
    subscription.setCreator_id(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    subscription = userSubscriptionRepository.createUserSubscription(subscription);
    if (subscription == null) {
      return ResponseFactory.conflict("Subscription for that user and service already exists");
    }
    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(SubscriptionEndpoint.class).build(subscription.getId()))
        .tag(etagService.getEtag(subscription))
        .entity(subscription)
        .build();
  }

  static class UserSub {
    @JsonProperty String id;
    @JsonProperty String subscription_uri;
    @JsonProperty String subscription_etag;
    @JsonProperty String service_id;
    @JsonProperty LocalizableString service_name;
    @JsonProperty UserSubscription.SubscriptionType subscription_type;
    @JsonProperty String creator_id;
    @JsonProperty String creator_name;
  }
}
