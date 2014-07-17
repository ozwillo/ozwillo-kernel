package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Strings;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/subscription/{subscription_id}")
@Authenticated @OAuth
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject EtagService etagService;

  @PathParam("subscription_id") String subscriptionId;

  @GET
  public Response get() {
    UserSubscription subscription = userSubscriptionRepository.getUserSubscription(subscriptionId);
    if (subscription == null) {
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok(subscription).build();
  }

  @DELETE
  public Response unsubscribe(@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // TODO: subscriptions with an organization_id can only be deleted by an admin of the organization
    // TODO: subscriptions without an organization_id can only be deleted by the user

    boolean deleted;
    try {
      deleted = userSubscriptionRepository.deleteUserSubscription(subscriptionId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.notFound("The subscription does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }
}
