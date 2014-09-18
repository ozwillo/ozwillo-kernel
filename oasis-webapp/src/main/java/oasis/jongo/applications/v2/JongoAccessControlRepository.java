package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

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
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ace.getUser_id()));

    ace = new JongoAccessControlEntry(ace);
    try {
      getAccessControlEntriesCollection()
          .insert(ace);
    } catch (DuplicateKeyException dke) {
      return null;
    }
    return ace;
  }

  @Override
  public AccessControlEntry getAccessControlEntry(String id) {
    return getAccessControlEntriesCollection()
        .findOne("{ id: # }", id)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public AccessControlEntry getAccessControlEntry(String instanceId, String userId) {
    return getAccessControlEntriesCollection()
        .findOne("{ instance_id: #, user_id: # }", instanceId, userId)
        .as(JongoAccessControlEntry.class);
  }

  @Override
  public boolean deleteAccessControlEntry(String id, long[] versions) throws InvalidVersionException {
    int n = getAccessControlEntriesCollection()
        .remove("{ id: #, modified: { $in: # } }", id, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getAccessControlEntriesCollection().count("{ id: # }", id) != 0) {
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
        .find("{ instance_id: # }", instanceId)
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
    getAccessControlEntriesCollection().ensureIndex("{ instance_id: 1, user_id: 1 }", "{ unique: 1 }");
    getAccessControlEntriesCollection().ensureIndex("{ instance_id: 1 }");
  }
}
