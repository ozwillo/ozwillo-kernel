package oasis.web.notifications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.Lists;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.notification.NotificationService;
import oasis.web.utils.ResponseFactory;

@Path("/n")
@Api(value = "/n", description = "Notification API")
// TODO: authentication
//@Authenticated
//@OAuth
public class NotificationEndpoint {

  @Inject
  NotificationRepository notificationRepository;

  @Inject
  NotificationService notificationService;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Publish a notification targeted to some users and/or groups")
  public Response publish(IncomingNotification incomingNotification
      //, @Context SecurityContext securityContext
  ) {
    // TODO: get applicationId from authentication
//    String applicationId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getServiceProviderId();
    String applicationId = "FAKE";

    notificationService.createNotifications(incomingNotification.groupIds, incomingNotification.userIds, incomingNotification.data,
        incomingNotification.message, applicationId);

    return ResponseFactory.NO_CONTENT;
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
    if (appId == null) {
      return Response.ok()
          .entity(notificationRepository.getNotifications(userId))
          .build();
    }
    return Response.ok()
        .entity(notificationRepository.getNotifications(userId, appId))
        .build();
  }

  @POST
  @Path("/{userId}/messages")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Change status (read, unread) of notifications for a user.")
  public Response post(
      @PathParam("userId") String userId,
      Mark mark
  ) {
    notificationRepository.markNotifications(userId, Lists.newArrayList(mark.messageIds), mark.status);
    return ResponseFactory.NO_CONTENT;
  }
}
