/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.jongo.applications.v2;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Longs;
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
    Preconditions.checkArgument(!Strings.isNullOrEmpty(subscription.getService_id()));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(subscription.getUser_id()));

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
        .remove("{ id: #, modified: { $in: # } }", id, Longs.asList(versions))
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
  @SuppressWarnings("unchecked")
  public Iterable<UserSubscription> getUserSubscriptions(String userId) {
    return (Iterable<UserSubscription>) (Iterable<?>) getUserSubscriptionsCollection()
        .find("{ user_id: # }", userId)
        .as(JongoUserSubscription.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<UserSubscription> getSubscriptionsForService(String serviceId) {
    return (Iterable<UserSubscription>) (Iterable<?>) getUserSubscriptionsCollection()
        .find("{ service_id: # }", serviceId)
        .as(JongoUserSubscription.class);
  }

  @Override
  public int deleteSubscriptionsForService(String serviceId) {
    return getUserSubscriptionsCollection()
        .remove("{ service_id: # }", serviceId)
        .getN();
  }

  @Override
  public int deleteSubscriptionsForServices(Collection<String> serviceIds) {
    if (serviceIds.isEmpty()) {
      return 0;
    }
    return getUserSubscriptionsCollection()
        .remove("{ service_id: { $in: # } }", ImmutableSet.copyOf(serviceIds))
        .getN();
  }

  @Override
  public void bootstrap() {
    getUserSubscriptionsCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ service_id: 1, user_id: 1 }", "{ unique: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ user_id: 1 }");
    getUserSubscriptionsCollection().ensureIndex("{ service_id: 1 }");
  }
}
