package oasis.web.applications;

import javax.annotation.Nullable;
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
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.i18n.LocalizableString;
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
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject ServiceRepository serviceRepository;
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
                sub.service_name = serviceRepository.getService(input.getService_id()).getName();
                sub.subscription_type = input.getSubscription_type();
                sub.creator_id = input.getCreator_id();
                // TODO: check access rights to the user name
                sub.creator_name = accountRepository.getUserAccountById(Objects.firstNonNull(input.getCreator_id(), input.getUser_id())).getName();
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
    if (!service.isVisible()) {
      // a personal subscription can only target a public service
      // TODO: support applications bought by individuals.
      return ResponseFactory.forbidden("Cannot subscribe to this service");
    }
    return createSubscription(subscription);
  }

  /** Called when the subscription_type is ORGANIZATION. */
  private Response subscribeOrganization(UserSubscription subscription) {
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    if (!isMemberOfOrganization(userId, service.getProvider_id())) {
      return ResponseFactory.unprocessableEntity("Target user is not a member of the organization");
    }
    if (!isAdminOfOrganization(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(),
        service.getProvider_id())) {
      return ResponseFactory.forbidden("Current user is not an administrator of target organization");
    }
    return createSubscription(subscription);
  }

  private boolean isMemberOfOrganization(String userId, String organizationId) {
    return organizationMembershipRepository.getOrganizationMembership(userId, organizationId) != null;
  }

  private boolean isAdminOfOrganization(String userId, String organizationId) {
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(userId, organizationId);
    if (membership == null) {
      return false;
    }
    return membership.isAdmin();
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
    @JsonProperty @Nullable String creator_id;
    @JsonProperty @Nullable String creator_name;
  }
}
