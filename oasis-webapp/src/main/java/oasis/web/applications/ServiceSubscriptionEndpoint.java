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
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/service/{service_id}")
@Authenticated @OAuth
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "subs-service", description = "User-Service subscriptions (from the service point of view)")
public class ServiceSubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("service_id") String serviceId;

  @GET
  @ApiOperation(
      value = "Retrieves users subscribed to the service",
      response = ServiceSub.class,
      responseContainer = "Array"
  )
  public Response getSubscriptions() {
    // TODO: only admins of the organization providing the service (or app instance?) can list subscriptions

    Iterable<UserSubscription> subscriptions = userSubscriptionRepository.getSubscriptionsForService(serviceId);
    return Response.ok()
        .entity(new GenericEntity<Iterable<ServiceSub>>(Iterables.transform(subscriptions,
            new Function<UserSubscription, ServiceSub>() {
              @Override
              public ServiceSub apply(UserSubscription input) {
                ServiceSub sub = new ServiceSub();
                sub.id = input.getId();
                sub.subscription_uri = uriInfo.getBaseUriBuilder().path(SubscriptionEndpoint.class).build(input.getId()).toString();
                sub.subscription_etag = etagService.getEtag(input);
                sub.user_id = input.getUser_id();
                sub.user_name = accountRepository.getUserAccountById(input.getUser_id()).getName();
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
      value = "Subscribes a user to the service; the user must be a member of the organization providing the service",
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
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    if (!isMemberOfOrganization(subscription.getUser_id(), service.getProvider_id())) {
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
    return Response.created(uriInfo.getAbsolutePathBuilder().path(SubscriptionEndpoint.class).build(subscription.getId()))
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
    @JsonProperty @Nullable String creator_id;
    @JsonProperty @Nullable String creator_name;
  }
}
