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
package oasis.jongo.eventbus;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.eventbus.Subscription;
import oasis.model.eventbus.SubscriptionRepository;

public class JongoSubscriptionRepository implements SubscriptionRepository, JongoBootstrapper {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionRepository.class);

  private final Jongo jongo;

  @Inject
  JongoSubscriptionRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Subscription createSubscription(Subscription subscription) {
    // TODO: validate subscription.eventType

    JongoSubscription jongoSubscription = new JongoSubscription(subscription);

    try {
      getSubscriptionsCollection()
          .insert(jongoSubscription);
    } catch (DuplicateKeyException e) {
      return null;
    }

    return jongoSubscription;
  }

  @Override
  public Subscription getSubscription(String subscriptionId) {
    return getSubscriptionsCollection()
        .findOne("{ id: # }", subscriptionId)
        .as(JongoSubscription.class);
  }

  @Override
  public boolean deleteSubscription(String subscriptionId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getSubscriptionsCollection()
        .remove("{ id: #, modified: { $in: # } }", subscriptionId, Longs.asList(versions));

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
  @SuppressWarnings("unchecked")
  public Iterable<Subscription> getSubscriptionsForEventType(String eventType) {
    return (Iterable<Subscription>) (Iterable<?>) getSubscriptionsCollection()
        .find("{ eventType: # }", eventType)
        .as(JongoSubscription.class);
  }

  @Override
  public int deleteSubscriptionsForAppInstance(String instance_id) {
    return getSubscriptionsCollection()
        .remove("{ instance_id: # }", instance_id)
        .getN();
  }

  @Override
  public void bootstrap() {
    getSubscriptionsCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getSubscriptionsCollection().ensureIndex("{ instance_id: 1, eventType: 1 }", "{ unique: 1 }");
    getSubscriptionsCollection().ensureIndex("{ eventType: 1 }");
  }

  private MongoCollection getSubscriptionsCollection() {
    return jongo.getCollection("subscriptions");
  }
}
