package oasis.jongo.eventbus;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.mongodb.WriteResult;

import oasis.jongo.applications.JongoApplication;
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
    WriteResult wr = getApplicationsCollection()
        .update("{ id: # , subscriptions.eventType: { $ne: # } }", appId, subscription.getEventType())
        .with("{ $push: { subscriptions: # } }", jongoSubscription);

    if (wr.getN() != 1) {
      logger.warn("The application {} does not exist or subscription already exists for that application and event type.", appId);
      return null;
    }
    return jongoSubscription;
  }

  @Override
  public boolean deleteSubscription(String subscriptionId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getApplicationsCollection()
        .update("{ subscriptions: { $elemMatch: { id: #, modified: { $in: # } } } }", subscriptionId, versions)
        .with("{ $pull: { subscriptions: { id: #, modified: { $in: # } } } }", subscriptionId, versions);

    int n = wr.getN();
    if (n == 0) {
      if (getApplicationsCollection().count("{ subscriptions.id: # }", subscriptionId) != 0) {
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
    Iterable<JongoApplication> apps = getApplicationsCollection()
        .find("{ subscriptions.eventType: # }", eventType)
        .projection("{ id: 1, subscriptions.$: 1}")
        .as(JongoApplication.class);
    return Iterables.transform(apps, new Function<JongoApplication, Subscription>() {
      @Override
      public Subscription apply(JongoApplication input) {
        return input.getSubscriptions().get(0);
      }
    });
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection("applications");
  }
}
