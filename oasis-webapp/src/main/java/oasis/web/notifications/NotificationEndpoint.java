package oasis.web.notifications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.collect.Lists;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.notification.NotificationService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/n")
@Api(value = "/n", description = "Notification API")
@Authenticated
public class NotificationEndpoint {

  @Inject NotificationRepository notificationRepository;
  @Inject NotificationService notificationService;
  @Inject ServiceRepository serviceRepository;

  @Context SecurityContext securityContext;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @Client
  @ApiOperation(value = "Publish a notification targeted to some users and/or groups")
  public Response publish(IncomingNotification incomingNotification) {
    String clientId = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();

    // TODO: check that the applicationId references a service for the given app-instance.
    Service service = serviceRepository.getService(incomingNotification.applicationId);
    if (service == null || !clientId.equals(service.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("Unknown service for the authenticated app-instance");
    }

    // XXX: check existence of users and groups?

    notificationService.createNotifications(incomingNotification.groupIds, incomingNotification.userIds, incomingNotification.data,
        incomingNotification.message, incomingNotification.applicationId);

    return ResponseFactory.NO_CONTENT;
  }

  @GET
  @Path("/{userId}/messages")
  @Produces(MediaType.APPLICATION_JSON)
  @OAuth
  @ApiOperation(value = "Get all unread notifications for a defined user and a filter.",
      response = Notification.class,
      responseContainer = "Array")
  public Response get(
      @PathParam("userId") String userId,
      @QueryParam("appId") String appId
  ) {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!oAuthUserId.equals(userId)) {
      return ResponseFactory.forbidden("Cannot read notifications for another user");
    }

    if (appId == null) {
      return Response.ok()
          .entity(new GenericEntity<Iterable<Notification>>(notificationRepository.getNotifications(userId)) {})
          .build();
    }
    return Response.ok()
        .entity(new GenericEntity<Iterable<Notification>>(notificationRepository.getNotifications(userId, appId)) {})
        .build();
  }

  @POST
  @Path("/{userId}/messages")
  @Consumes(MediaType.APPLICATION_JSON)
  @OAuth
  @ApiOperation(value = "Change status (read, unread) of notifications for a user.")
  public Response post(
      @PathParam("userId") String userId,
      Mark mark
  ) {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!oAuthUserId.equals(userId)) {
      return ResponseFactory.forbidden("Cannot change read status for another user's notifications");
    }

    notificationRepository.markNotifications(userId, Lists.newArrayList(mark.messageIds), mark.status);
    return ResponseFactory.NO_CONTENT;
  }
}
