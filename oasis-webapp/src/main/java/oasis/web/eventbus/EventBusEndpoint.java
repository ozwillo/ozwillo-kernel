package oasis.web.eventbus;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.eventbus.Subscription;
import oasis.model.eventbus.SubscriptionRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.utils.ResponseFactory;
import oasis.web.webhooks.WebhookSignatureFilter;

@Authenticated
@Client
@Path("/e")
@Api(value = "/e", description = "EventBus API")
public class EventBusEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(EventBusEndpoint.class);

  @Inject SubscriptionRepository subscriptionRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject EtagService etagService;
  @Inject javax.ws.rs.client.Client client;

  @Context SecurityContext securityContext;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Publish a typed event into the event bus.")
  public Response publish(
      final Event event
  ) {
    final String providerId = getProviderId(((ClientPrincipal) securityContext.getUserPrincipal()).getClientId());
    for (Subscription subscription : subscriptionRepository.getSubscriptionsForEventType(event.eventType)) {
      if (!providerId.equals(getProviderId(subscription.getInstance_id()))) {
        // Don't send events to instances from other providers
        continue;
      }

      final String webhook = subscription.getWebHook();

      // TODO: better eventbus system
      try {
        client
            .register(new WebhookSignatureFilter(subscription.getSecret()))
            .target(webhook).request()
            .async().post(Entity.json(event), new InvocationCallback<Response>() {
              @Override
              public void completed(Response response) {
                try {
                  logger.trace("Webhook {} called for eventType {}.", webhook, event.eventType);
                } finally {
                  response.close();
                }
              }

              @Override
              public void failed(Throwable throwable) {
                logger.error("Error calling webhook {} for eventType {}: {}.", webhook, event.eventType, throwable.getMessage(), throwable);
              }
            });
      } catch (Throwable t) {
        logger.error("Error calling webhook {} for eventType {}: {}", webhook, event.eventType, t.getMessage(), t);
      }
    }

    return ResponseFactory.NO_CONTENT;
  }

  private String getProviderId(String instanceId) {
    AppInstance appInstance = appInstanceRepository.getAppInstance(instanceId);
    if (appInstance == null) {
      return null;
    }
    if (appInstance.getProvider_id() != null) {
      return "org:" + appInstance.getProvider_id();
    }
    if (appInstance.getInstantiator_id() != null) {
      return "user:" + appInstance.getInstantiator_id();
    }
    return null;
  }

  @POST
  @Path("/{instanceId}/subscriptions")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Subscribe to a typed event from the event bus.",
      notes = "The returned location URL get access to the subscription (delete the subscription)")
  public Response subscribe(
      @PathParam("instanceId") String instanceId,
      Subscription subscription
  ) {
    if (appInstanceRepository.getAppInstance(instanceId) == null) {
      return ResponseFactory.notFound("The application instance does not exist");
    }

    if (!instanceId.equals(((ClientPrincipal) securityContext.getUserPrincipal()).getClientId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (!Strings.isNullOrEmpty(subscription.getInstance_id()) && !instanceId.equals(subscription.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    subscription.setInstance_id(instanceId);
    // TODO: validate subscription.eventType

    Subscription res = subscriptionRepository.createSubscription(subscription);
    if (res == null) {
      return ResponseFactory.conflict("Subscription already exists for this application instance and event type");
    }

    URI uri = UriBuilder
        .fromResource(EventBusEndpoint.class)
        .path(EventBusEndpoint.class, "unsubscribe")
        .build(res.getId());
    return Response
        .created(uri)
        .contentLocation(uri)
        .tag(etagService.getEtag(res))
        .build();

  }

  @DELETE
  @Path("/subscription/{subscriptionId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Delete a subscription.")
  public Response unsubscribe(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("subscriptionId") String subscriptionId
  ) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    Subscription subscription = subscriptionRepository.getSubscription(subscriptionId);
    if (subscription == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(subscription.getInstance_id())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    boolean deleted;
    try {
      deleted = subscriptionRepository.deleteSubscription(subscriptionId, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.notFound("The subscription does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }
}
