package oasis.model.applications;

public interface SubscriptionRepository {

  /**
   * @return the generated subscription id
   */
  String createSubscription(String appId, Subscription subscription);

  boolean deleteSubscription(String subscriptionId);

  Subscription getSomeSubscriptionForEventType(String eventType);
}
