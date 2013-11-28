package oasis.web.notifications;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;

@Path("/n")
@Api(value = "/n", description = "Notification API")
public class NotificationEndpoint {

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Publish a notification targeted to some users and/or groups")
  public Response publish(
      IncomingNotification incomingNotification
  ) {
    //TODO: Make some cool stuff
    throw new UnsupportedOperationException();
  }

  @GET
  @Path("/{userId}/messages")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get all unread notifications for a defined user and a filter.",
      response = Notification.class,
      responseContainer = "Array")
  public Response get(
      @PathParam("userId") String userId,
      @QueryParam("appId") String appId
  ) {
    //TODO: Put some funky code here
    throw new UnsupportedOperationException();
  }

  @POST
  @Path("/{userId}/messages")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Change status (read, unread) of notifications for a user.")
  public Response post(
      @PathParam("userId") String userId,
      Mark mark
  ) {
    //TODO: Make some magic
    throw new UnsupportedOperationException();
  }

  //TODO : Other functions here ?

  @ApiModel
  static class Mark {

    @JsonProperty
    @ApiModelProperty
    String[] messageIds;

    @JsonProperty
    @ApiModelProperty
    String status;
  }

  @ApiModel
  static class Notification {

    @JsonProperty
    @ApiModelProperty
    String data;

    @JsonProperty
    @ApiModelProperty
    String message;

    @JsonProperty
    @ApiModelProperty
    Instant time;
  }

  @ApiModel
  static class IncomingNotification {

    @JsonProperty
    @ApiModelProperty
    String[] userIds;

    @JsonProperty
    @ApiModelProperty
    String[] groupIds;

    @JsonProperty
    @ApiModelProperty
    Notification notification;
  }
}