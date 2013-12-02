package oasis.model.applications;

public interface SubscriptionRepository {

  /**
   * @return the generated subscription id
   */
  public String createSubscription(String appId, Subscription subscription);

  public boolean deleteSubscription(String subscriptionId);

  public Subscription getSomeSubscriptionForEventType(String eventType);
}
