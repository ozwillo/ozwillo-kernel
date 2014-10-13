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
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

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
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/service/{service_id}")
@Authenticated @OAuth
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "subs-service", description = "User-Service subscriptions (from the service point of view)")
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
  @ApiOperation(
      value = "Retrieves users subscribed to the service (filtered to only the app_users)",
      response = ServiceSub.class,
      responseContainer = "Array"
  )
  public Response getSubscriptions() {
    AppInstance instance = getAppInstance();
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }

    Iterable<UserSubscription> subscriptions = userSubscriptionRepository.getSubscriptionsForService(serviceId);

    // Filter the list to only the app_users.
    final ImmutableSet<String> app_users = FluentIterable.from(accessControlRepository.getAccessControlListForAppInstance(instance.getId()))
        .transform(new Function<AccessControlEntry, String>() {
          @Override
          public String apply(AccessControlEntry input) {
            return input.getUser_id();
          }
        })
        .toSet();
    subscriptions = Iterables.filter(subscriptions, new Predicate<UserSubscription>() {
      @Override
      public boolean apply(UserSubscription input) {
        return app_users.contains(input.getUser_id());
      }
    });

    return Response.ok()
        .entity(new GenericEntity<Iterable<ServiceSub>>(Iterables.transform(subscriptions,
            new Function<UserSubscription, ServiceSub>() {
              @Override
              public ServiceSub apply(UserSubscription input) {
                ServiceSub sub = new ServiceSub();
                sub.id = input.getId();
                sub.subscription_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(SubscriptionEndpoint.class).build(input.getId()).toString();
                sub.subscription_etag = etagService.getEtag(input);
                sub.user_id = input.getUser_id();
                final UserAccount user = accountRepository.getUserAccountById(input.getUser_id());
                sub.user_name = user == null ? null : user.getDisplayName();
                sub.subscription_type = input.getSubscription_type();
                sub.creator_id = Objects.firstNonNull(input.getCreator_id(), input.getUser_id());
                // TODO: check access rights to the user name
                // TODO: introduce some caching (it's not unlikely many subscriptions will have the same creator)
                final UserAccount creator = accountRepository.getUserAccountById(sub.creator_id);
                sub.creator_name = creator == null ? null : creator.getDisplayName();
                return sub;
              }
            })) {})
        .build();
  }

  @POST
  @ApiOperation(
      value = "Subscribes a user to the service; the user must be an app_user for the app-instance",
      response = UserSubscription.class
  )
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
    if (accessControlRepository.getAccessControlEntry(instance.getId(), subscription.getUser_id()) == null) {
      return ResponseFactory.unprocessableEntity("Target user is not an app_user for the service");
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
    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(SubscriptionEndpoint.class).build(subscription.getId()))
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
