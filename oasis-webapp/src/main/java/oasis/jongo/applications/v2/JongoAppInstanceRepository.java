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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;

public class JongoAppInstanceRepository implements AppInstanceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceRepository.class);
  public static final String COLLECTION_NAME = "app_instances";

  private final Jongo jongo;

  @Inject
  JongoAppInstanceRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public AppInstance createAppInstance(AppInstance instance) {
    JongoAppInstance jongoAppInstance = new JongoAppInstance(instance);
    jongoAppInstance.initCreated();
    try {
      getAppInstancesCollection().insert(jongoAppInstance);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoAppInstance;
  }

  @Override
  public AppInstance getAppInstance(String instanceId) {
    return getAppInstancesCollection()
        .findOne("{ id: # }", instanceId)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> getAppInstances(Collection<String> instanceIds) {
    if (instanceIds.isEmpty()) {
      return Collections.emptyList();
    }
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ id: { $in: # } }", ImmutableSet.copyOf(instanceIds))
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findByOrganizationId(String organizationId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ provider_id: # }", organizationId)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findByOrganizationIdAndStatus(String organizationId, AppInstance.InstantiationStatus instantiationStatus) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ provider_id: #, status: # }", organizationId, instantiationStatus)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findPersonalInstancesByUserId(String userId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ instantiator_id: #, provider_id: { $exists: 0 } }", userId)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findPersonalInstancesByUserIdAndStatus(String userId, AppInstance.InstantiationStatus instantiationStatus) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ instantiator_id: #, status: #, provider_id: { $exists: 0 } }", userId, instantiationStatus)
        .as(JongoAppInstance.class);
  }

  @Override
  public long getNonStoppedCountByOrganizationId(String organizationId) {
    return getAppInstancesCollection()
        .count("{ provider_id: #, status: { $ne: # } }", organizationId, AppInstance.InstantiationStatus.STOPPED);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findStoppedBefore(Instant stoppedBefore) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ status: #, status_changed: { $lt: # } }", AppInstance.InstantiationStatus.STOPPED, stoppedBefore.toDate())
        .as(JongoAppInstance.class);
  }

  @Override
  public AppInstance updateStatus(String instanceId, AppInstance.InstantiationStatus newStatus, String statusChangeRequesterId) {
    checkArgument(checkNotNull(newStatus) != AppInstance.InstantiationStatus.PENDING);

    Instant now = Instant.now();
    return getAppInstancesCollection()
        // let's ensure that the updated app instance is not pending
        .findAndModify("{ id: #, status: { $ne: # } }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), statusChangeRequesterId, now.getMillis())
        .as(JongoAppInstance.class);
  }

  @Override
  public AppInstance updateStatus(String instanceId, AppInstance.InstantiationStatus newStatus, String statusChangeRequesterId, long[] versions)
      throws InvalidVersionException {
    checkArgument(checkNotNull(newStatus) != AppInstance.InstantiationStatus.PENDING);

    Instant now = Instant.now();
    JongoAppInstance appInstance = getAppInstancesCollection()
        // let's ensure that the updated app instance is not pending
        .findAndModify("{ id: #, status: { $ne: # }, modified: { $in: # } }", instanceId, AppInstance.InstantiationStatus.PENDING, Longs.asList(versions))
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), statusChangeRequesterId, now.getMillis())
        .as(JongoAppInstance.class);

    if (appInstance == null) {
      if (getAppInstancesCollection().count("{ id: #, status: { $ne: # } }", instanceId, AppInstance.InstantiationStatus.PENDING) > 0) {
        throw new InvalidVersionException("app-instance", instanceId);
      }
    }
    return appInstance;
  }

  @Override
  public AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes, String destruction_uri, String destruction_secret,
      String status_changed_uri, String status_changed_secret, AppInstance.InstantiationStatus status) {
    Preconditions.checkArgument(status == AppInstance.InstantiationStatus.RUNNING || status == AppInstance.InstantiationStatus.STOPPED);
    AppInstance instance = getAppInstancesCollection()
        .findAndModify("{ id: #, status: # }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .with("{ $set: { status: #, needed_scopes: #, destruction_uri: #, destruction_secret: #, status_changed_uri: #, status_changed_secret: #, provisioned: # } }",
            status, neededScopes, destruction_uri, destruction_secret, status_changed_uri, status_changed_secret, System.currentTimeMillis())
        .as(AppInstance.class);
    return instance;
  }

  @Override
  public AppInstance backToPending(String instanceId) {
    return getAppInstancesCollection()
        .findAndModify("{ id: #, status: { $ne: # } }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .with("{ $set: { status: # }, $unset: { needed_scopes: 1, destruction_uri: 1, destruction_secret: 1, provisioned: 1 } }",
            AppInstance.InstantiationStatus.PENDING)
        .as(AppInstance.class);
  }

  @Override
  public boolean deleteInstance(String instanceId) {
    return getAppInstancesCollection()
        .remove("{ id: # }", instanceId)
        .getN() != 0;
  }

  @Override
  public boolean deleteInstance(String instanceId, long[] versions) throws InvalidVersionException {
    int n = getAppInstancesCollection()
        .remove("{ id: #, modified: { $in: # } }", instanceId, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getAppInstancesCollection().count("{ id: # }", instanceId) != 0) {
        throw new InvalidVersionException("app-instance", instanceId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} app instances with ID {}, that shouldn't have happened", n, instanceId);
    }
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> getInstancesForApplication(String applicationId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ application_id: # }", applicationId)
        .as(JongoAppInstance.class);
  }

  @Override
  public void bootstrap() {
    getAppInstancesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
  }

  private MongoCollection getAppInstancesCollection() {
    return jongo.getCollection(COLLECTION_NAME);
  }
}
