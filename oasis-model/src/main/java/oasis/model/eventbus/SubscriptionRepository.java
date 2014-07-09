package oasis.model.eventbus;

import oasis.model.InvalidVersionException;

public interface SubscriptionRepository {

  /**
   * @return the generated subscription id
   */
  Subscription createSubscription(String appId, Subscription subscription);

  boolean deleteSubscription(String subscriptionId, long[] versions) throws InvalidVersionException;

  Iterable<Subscription> getSubscriptionsForEventType(String eventType);
}
