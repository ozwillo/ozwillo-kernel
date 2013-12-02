package oasis.services.applications;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.WriteResult;

import oasis.model.applications.Subscription;
import oasis.model.applications.SubscriptionRepository;

public class JongoSubscriptionRepository implements SubscriptionRepository {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionRepository.class);

  private final Jongo jongo;

  @Inject
  JongoSubscriptionRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public String createSubscription(String appId, Subscription subscription) {
    // TODO: validate subscription.eventType

    WriteResult wr = getApplicationsCollection()
        .update("{ id: # , subscriptions.eventType: { $ne: # } }", appId, subscription.getEventType())
        .with("{ $push: { subscriptions: # } }", subscription);

    if (wr.getN() != 1) {
      logger.warn("The application {} does not exist or subscription already exists for that application and event type.", appId);
      return null;
    }
    return subscription.getId();
  }

  @Override
  public boolean deleteSubscription(String subscriptionId) {
    // TODO: check modified (add modified in Subscription)
    WriteResult wr = getApplicationsCollection()
        .update("{ subscriptions.id: # }", subscriptionId)
        .with("{ $pull: { subscriptions: { id: # } } }", subscriptionId);

    int n = wr.getN();
    if (n > 1) {
      logger.error("Deleted {} subscriptions with ID {}, that shouldn't have happened",
          n, subscriptionId);
    }
    return n > 0;
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection("applications");
  }
}
