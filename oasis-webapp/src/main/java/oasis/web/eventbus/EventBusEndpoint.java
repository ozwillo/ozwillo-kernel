package oasis.web.eventbus;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import oasis.model.applications.Subscription;
import oasis.model.applications.SubscriptionRepository;
import oasis.services.etag.EtagService;
import oasis.web.ResponseFactory;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;

@Authenticated
@Client
@Path("/e")
@Api(value = "/e", description = "EventBus API")
public class EventBusEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(EventBusEndpoint.class);
  private static final String SECRET_HEADER = "X-Webhook-Secret";

  @Inject
  SubscriptionRepository subscriptions;
  @Inject
  EtagService etagService;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Publish a typed event into the event bus.",
      notes = "The header '" + SECRET_HEADER + "' contains the secret defined by the application for this subscription." +
          "This will be replaced by a response signature.")
  public Response publish(
      final Event event
  ) {
    final Subscription subscription = subscriptions.getSomeSubscriptionForEventType(event.eventType);
    if (subscription == null) {
      logger.error("No subscription found for this eventType {}.", event.eventType);
      return ResponseFactory.NO_CONTENT;
    }

    final String webhook = subscription.getWebHook();

    // TODO: better eventbus system
    // TODO: compute signature rather than sending the secret
    ClientBuilder.newClient().target(subscription.getWebHook()).request().header(SECRET_HEADER, subscription.getSecret()).async()
        .post(Entity.json(event), new InvocationCallback<Object>() {
          @Override
          public void completed(Object o) {
            logger.trace("Webhook {} called for eventType {}.", webhook, event.eventType);
          }

          @Override
          public void failed(Throwable throwable) {
            logger.error("Error calling webhook {} for eventType {} : {}.", webhook, event.eventType, throwable.getMessage());
          }
        });

    return ResponseFactory.NO_CONTENT;
  }

  @POST
  @Path("/{appId}/subscriptions")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Subscribe to a typed event from the event bus.",
      notes = "The returned location URL get access to the subscription (delete the subscription)")
  public Response subscribe(
      @PathParam("appId") String appId,
      Subscription subscription
  ) {
    // TODO: validate subscription.eventType

    Subscription res = subscriptions.createSubscription(appId, subscription);
    if (res == null) {
      return ResponseFactory.notFound("The application does not exist");
    }

    URI uri = UriBuilder
        .fromResource(EventBusEndpoint.class)
        .path(EventBusEndpoint.class, "unsubscribe")
        .build(subscription.getId());
    return Response
        .created(uri)
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

    if (!subscriptions.deleteSubscription(subscriptionId)) {
      return ResponseFactory.notFound("The subscription does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }
}
