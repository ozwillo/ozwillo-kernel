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
