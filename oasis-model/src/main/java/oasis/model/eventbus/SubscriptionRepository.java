package oasis.model.eventbus;

import oasis.model.InvalidVersionException;

public interface SubscriptionRepository {

  Subscription createSubscription(Subscription subscription);

  boolean deleteSubscription(String subscriptionId, long[] versions) throws InvalidVersionException;

  Iterable<Subscription> getSubscriptionsForEventType(String eventType);

  int deleteSubscriptionsForAppInstance(String instance_id);
}
