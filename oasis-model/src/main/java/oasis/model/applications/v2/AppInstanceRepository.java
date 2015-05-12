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
import java.util.List;

import org.joda.time.Instant;

import oasis.model.InvalidVersionException;

public interface AppInstanceRepository {
  AppInstance createAppInstance(AppInstance appInstance);

  AppInstance getAppInstance(String instanceId);

  Iterable<AppInstance> getAppInstances(Collection<String> instanceIds);

  Iterable<AppInstance> findByOrganizationId(String organizationId);

  Iterable<AppInstance> findByOrganizationIdAndStatus(String organizationId, AppInstance.InstantiationStatus instantiationStatus);

  Iterable<AppInstance> findPersonalInstancesByUserId(String userId);

  Iterable<AppInstance> findPersonalInstancesByUserIdAndStatus(String userId, AppInstance.InstantiationStatus instantiationStatus);

  long getNonStoppedCountByOrganizationId(String organizationId);

  Iterable<AppInstance> findStoppedBefore(Instant stoppedBefore);

  AppInstance updateStatus(String instanceId, AppInstance.InstantiationStatus newStatus, String statusChangeRequesterId);

  AppInstance updateStatus(String instanceId, AppInstance.InstantiationStatus newStatus, String statusChangeRequesterId, long[] versions)
      throws InvalidVersionException;

  AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes, String destruction_uri, String destruction_secret,
      String status_changed_uri, String status_changed_secret, AppInstance.InstantiationStatus status);

  AppInstance backToPending(String instanceId);

  boolean deleteInstance(String instanceId, long[] versions) throws InvalidVersionException;

  boolean deleteInstance(String instanceId);

  Iterable<AppInstance> getInstancesForApplication(String applicationId);
}
