package oasis.model.applications.v2;

import java.util.Collection;

import oasis.model.InvalidVersionException;

public interface UserSubscriptionRepository {
  UserSubscription createUserSubscription(UserSubscription subscription);

  UserSubscription getUserSubscription(String id);

  boolean deleteUserSubscription(String id, long[] versions) throws InvalidVersionException;

  Iterable<UserSubscription> getUserSubscriptions(String userId);

  Iterable<UserSubscription> getSubscriptionsForService(String serviceId);

  int deleteSubscriptionsForService(String serviceId);

  int deleteSubscriptionsForServices(Collection<String> serviceIds);
}
