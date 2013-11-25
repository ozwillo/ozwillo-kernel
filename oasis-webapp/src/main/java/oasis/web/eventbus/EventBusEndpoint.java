package oasis.web.eventbus;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;

@Authenticated
@Client
@Path("/e")
@Api(value = "/e", description = "EventBus API")
public class EventBusEndpoint {

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
      notes = "The returned URL get access to the subscription (used to delete the subscription)",
      response = URI.class)
  public Response subscribe(
      @PathParam("appId") String appId,
      Subscription subscription
  ) {
    //TODO: Code that make us dream
    throw new UnsupportedOperationException();
  }

  @DELETE
  @Path("/{appId}/subscription/{eventType}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Unsubscribe to a typed event from the event bus.")
  public Response unsubscribe(
      @PathParam("appId") String appId,
      @PathParam("eventType") String eventType // Unique (gives the application for an organisation)
  ) {
    //TODO: Code that don't make us cry
    throw new UnsupportedOperationException();
  }

  //TODO : Other functions here ?

  @ApiModel
  static class Subscription {

    @JsonProperty()
    @ApiModelProperty
    String webHook;

    @JsonProperty()
    @ApiModelProperty
    String secret;

    @JsonProperty()
    @ApiModelProperty
    String eventType; // Unique (gives the application for an organisation)
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
