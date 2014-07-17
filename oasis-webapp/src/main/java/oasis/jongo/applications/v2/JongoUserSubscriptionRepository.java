package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;

public class JongoUserSubscriptionRepository implements UserSubscriptionRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JongoUserSubscriptionRepository.class);

  private final Jongo jongo;

  @Inject JongoUserSubscriptionRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getUserSubscriptionsCollection() {
    return jongo.getCollection("user_subscriptions");
  }

  @Override
  public UserSubscription createUserSubscription(UserSubscription subscription) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(subscription.getId()));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(subscription.getService_id()));

    subscription = new JongoUserSubscription(subscription);
    try {
      getUserSubscriptionsCollection()
          .insert(subscription);
    } catch (DuplicateKeyException dke) {
      return null;
    }
    return subscription;
  }

  @Override
  public UserSubscription getUserSubscription(String id) {
    return getUserSubscriptionsCollection()
        .findOne("{ id: # }", id)
        .as(JongoUserSubscription.class);
  }

  @Override
  public boolean deleteUserSubscription(String id, long[] versions) throws InvalidVersionException {
    int n = getUserSubscriptionsCollection()
        .remove("{ id: #, modified: { $in: # } }", id, versions)
        .getN();

    if (n == 0) {
      if (getUserSubscriptionsCollection().count("{ id: # }", id) != 0) {
        throw new InvalidVersionException("userSubscription", id);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} user subscriptions with ID {}, that shouldn't have happened", n, id);
    }
    return true;
  }

  @Override
  public Iterable<UserSubscription> getUserSubscriptions(String userId) {
    return getUserSubscriptionsCollection()
        .find("{ user_id: # }", userId)
        .as(UserSubscription.class);
  }

  @Override
  public Iterable<UserSubscription> getSubscriptionsForService(String serviceId) {
    return getUserSubscriptionsCollection()
        .find("{ service_id: 1 }")
        .as(UserSubscription.class);
  }

  @Override
  public void bootstrap() {
    getUserSubscriptionsCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ service_id: 1, user_id: 1 }", "{ unique: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ user_id: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ service_id: 1 }");
  }
}
