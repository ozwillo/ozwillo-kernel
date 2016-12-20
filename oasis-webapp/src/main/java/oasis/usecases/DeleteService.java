/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.usecases;

import javax.inject.Inject;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;

public class DeleteService {

  private final ServiceRepository serviceRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;

  @Inject DeleteService(ServiceRepository serviceRepository, UserSubscriptionRepository userSubscriptionRepository) {
    this.serviceRepository = serviceRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
  }

  public Status deleteService(String service_id, long[] versions) {
    boolean deleted = false;
    try {
      deleted = serviceRepository.deleteService(service_id, versions);
    } catch (InvalidVersionException e) {
      return Status.BAD_SERVICE_VERSION;
    }

    int deletedSubscriptions = userSubscriptionRepository.deleteSubscriptionsForService(service_id);

    if (deleted) {
      return Status.DELETED_SERVICE;
    } else if (deletedSubscriptions == 0) {
      return Status.NOTHING_TO_DELETE;
    }
    return Status.DELETED_LEFTOVERS;
  }

  public enum Status {
    BAD_SERVICE_VERSION,
    DELETED_SERVICE,
    DELETED_LEFTOVERS,
    NOTHING_TO_DELETE
  }
}
