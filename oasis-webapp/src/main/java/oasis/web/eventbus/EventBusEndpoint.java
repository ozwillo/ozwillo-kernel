package oasis.web.eventbus;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.Subscription;
import oasis.model.applications.SubscriptionRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;

@Authenticated
@Client
@Path("/e")
@Api(value = "/e", description = "EventBus API")
public class EventBusEndpoint {

  @Inject
  ApplicationRepository applications;

  @Inject
  SubscriptionRepository subscriptions;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Publish a typed event into the event bus.")
  public Response publish(
      Event event
  ) {
    //TODO: Awesome code here
    throw new UnsupportedOperationException();
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

    if (subscriptions.createSubscription(appId, subscription) == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("The application does not exist")
          .build();
    }

    return Response
        .created(UriBuilder
            .fromResource(EventBusEndpoint.class)
            .path(EventBusEndpoint.class, "unsubscribe")
            .build(subscription.getId()))
        .build();
  }

  @DELETE
  @Path("/subscription/{subscriptionId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Delete a subscription.")
  public Response unsubscribe(
      @PathParam("subscriptionId") String subscriptionId
  ) {
    if (!subscriptions.deleteSubscription(subscriptionId)) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("The subscription does not exist")
          .build();
    }

    return Response.noContent().build();
  }

  @ApiModel
  static class Event {

    @JsonProperty()
    @ApiModelProperty
    String message;

    @JsonProperty()
    @ApiModelProperty
    String data;

    @JsonProperty()
    @ApiModelProperty
    String eventType; // Unique (gives the application for an organisation)
  }

}
