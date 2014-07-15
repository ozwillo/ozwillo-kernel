package oasis.jongo.eventbus;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.WriteResult;

import oasis.model.InvalidVersionException;
import oasis.model.eventbus.Subscription;
import oasis.model.eventbus.SubscriptionRepository;

public class JongoSubscriptionRepository implements SubscriptionRepository {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionRepository.class);

  private final Jongo jongo;

  @Inject
  JongoSubscriptionRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Subscription createSubscription(String appId, Subscription subscription) {
    // TODO: validate subscription.eventType

    JongoSubscription jongoSubscription = new JongoSubscription(subscription);
    jongoSubscription.setApplication_id(appId);
    // TODO: replace with insert() once we setup a unique index
    WriteResult wr = getSubscriptionsCollection()
        .update("{ application_id: # , eventType: { $ne: # } }", appId, subscription.getEventType())
        .upsert()
        .with(jongoSubscription);

    if (wr.getN() != 1) {
      logger.warn("The application {} does not exist or subscription already exists for that application and event type.", appId);
      return null;
    }
    return jongoSubscription;
  }

  @Override
  public boolean deleteSubscription(String subscriptionId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getSubscriptionsCollection()
        .remove("{ id: #, modified: { $in: # } }", subscriptionId, versions);

    int n = wr.getN();
    if (n == 0) {
      if (getSubscriptionsCollection().count("{ id: # }", subscriptionId) != 0) {
        throw new InvalidVersionException("subscription", subscriptionId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} subscriptions with ID {}, that shouldn't have happened", n, subscriptionId);
    }
    return true;
  }

  @Override
  public Iterable<Subscription> getSubscriptionsForEventType(String eventType) {
    return getSubscriptionsCollection()
        .find("{ eventType: # }", eventType)
        .as(Subscription.class);
  }

  private MongoCollection getSubscriptionsCollection() {
    return jongo.getCollection("subscriptions");
  }
}
