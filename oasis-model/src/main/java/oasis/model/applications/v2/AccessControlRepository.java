package oasis.model.applications.v2;

import oasis.model.InvalidVersionException;

public interface AccessControlRepository {
  AccessControlEntry createAccessControlEntry(AccessControlEntry accessControlEntry);

  AccessControlEntry getAccessControlEntry(String id);

  AccessControlEntry getAccessControlEntry(String instanceId, String userId);

  boolean deleteAccessControlEntry(String id, long[] versions) throws InvalidVersionException;

  Iterable<AccessControlEntry> getAccessControlListForAppInstance(String instanceId);
}
