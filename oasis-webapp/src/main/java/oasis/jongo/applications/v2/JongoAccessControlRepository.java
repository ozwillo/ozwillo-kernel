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
package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoCommandException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;

public class JongoAccessControlRepository implements AccessControlRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JongoAccessControlRepository.class);

  private final Jongo jongo;

  @Inject JongoAccessControlRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getAccessControlEntriesCollection() {
    return jongo.getCollection("app_instance_aces");
  }

  @Override
  public AccessControlEntry createAccessControlEntry(AccessControlEntry ace) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ace.getInstance_id()));
    Preconditions.checkArgument((
        !Strings.isNullOrEmpty(ace.getUser_id())
            && ace.getStatus() == AccessControlEntry.Status.ACCEPTED
            && Strings.isNullOrEmpty(ace.getEmail())
        ) || (
        !Strings.isNullOrEmpty(ace.getEmail())
            && ace.getStatus() == AccessControlEntry.Status.PENDING
            && Strings.isNullOrEmpty(ace.getUser_id())));

    JongoAccessControlEntry entry = new JongoAccessControlEntry(ace);
    entry.setCreated(Instant.now());
    try {
      getAccessControlEntriesCollection()
          .insert(entry);
    } catch (DuplicateKeyException dke) {
      return null;
    }
    return entry;
  }

  @Override
  public AccessControlEntry getAccessControlEntry(String id) {
    return getAccessControlEntriesCollection()
        .findOne("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", id, AccessControlEntry.Status.ACCEPTED)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public AccessControlEntry getPendingAccessControlEntry(String id) {
    return getAccessControlEntriesCollection()
        .findOne("{ id: #, status: # }", id, AccessControlEntry.Status.PENDING)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public AccessControlEntry getAccessControlEntry(String instanceId, String userId) {
    return getAccessControlEntriesCollection()
        .findOne("{ instance_id: #, user_id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
            instanceId, userId, AccessControlEntry.Status.ACCEPTED)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public AccessControlEntry acceptPendingAccessControlEntry(String aceId, String userId) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(aceId));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(userId));

    try {
      return getAccessControlEntriesCollection()
          .findAndModify("{ id: #, status: # }", aceId, AccessControlEntry.Status.PENDING)
          .returnNew()
          .with("{ $set: { status: #, user_id: #, accepted: # }, $unset: { email: '' } }",
              AccessControlEntry.Status.ACCEPTED, userId, System.currentTimeMillis())
          .as(JongoAccessControlEntry.class);
    } catch (DuplicateKeyException e) {
      throw new oasis.model.DuplicateKeyException();
    }
  }

  @Override
  public boolean deleteAccessControlEntry(String id, long[] versions) throws InvalidVersionException {
    int n = getAccessControlEntriesCollection()
        .remove("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ], modified: { $in: # } }",
            id, AccessControlEntry.Status.ACCEPTED, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getAccessControlEntriesCollection().count("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
          id, AccessControlEntry.Status.ACCEPTED) != 0) {
        throw new InvalidVersionException("ace", id);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} access control entries with ID {}, that shouldn't have happened", n, id);
    }
    return true;
  }

  @Override
  public boolean deletePendingAccessControlEntry(String id) {
    int n = getAccessControlEntriesCollection()
        .remove("{ id: #, status: # }", id, AccessControlEntry.Status.PENDING)
        .getN();
    return n > 0;
  }

  @Override
  public boolean deletePendingAccessControlEntry(String id, long[] versions) throws InvalidVersionException {
    int n = getAccessControlEntriesCollection()
        .remove("{ id: #, status: #, modified: { $in: # } }",
            id, AccessControlEntry.Status.PENDING, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getAccessControlEntriesCollection().count("{ id: #, status: # }", id, AccessControlEntry.Status.PENDING) != 0) {
        throw new InvalidVersionException("ace", id);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} access control entries with ID {}, that shouldn't have happened", n, id);
    }
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AccessControlEntry> getAccessControlListForAppInstance(String instanceId) {
    return (Iterable<AccessControlEntry>) (Iterable<?>) getAccessControlEntriesCollection()
        .find("{ instance_id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", instanceId, AccessControlEntry.Status.ACCEPTED)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AccessControlEntry> getPendingAccessControlListForAppInstance(String instanceId) {
    return (Iterable<AccessControlEntry>) (Iterable<?>) getAccessControlEntriesCollection()
        .find("{ instance_id: #, status: # }", instanceId, AccessControlEntry.Status.PENDING)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public int deleteAccessControlListForAppInstance(String instanceId) {
    return getAccessControlEntriesCollection()
        .remove("{ instance_id: # }", instanceId)
        .getN();
  }

  @Override
  public void bootstrap() {
    getAccessControlEntriesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getAccessControlEntriesCollection().ensureIndex("{ instance_id: 1, user_id: 1, email: 1 }", "{ unique: 1 }");
    try {
      getAccessControlEntriesCollection().dropIndex("{ instance_id: 1, user_id: 1 }");
      logger.info("Deleted previous index on instance_id+user_id (replaced by index on instance_id+user_id+email)");
    } catch (MongoCommandException mce) {
      // ignore
    }
    getAccessControlEntriesCollection().ensureIndex("{ instance_id: 1 }");
  }
}
