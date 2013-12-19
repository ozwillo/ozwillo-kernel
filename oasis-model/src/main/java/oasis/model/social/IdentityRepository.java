package oasis.model.social;

import java.util.Collection;

public interface IdentityRepository {
  Identity getIdentity(String identityId);

  boolean addRelation(String sourceIdentityId, String relationType, String destIdentityId);

  boolean removeRelation(String sourceIdentityId, String relationType, String destIdentityId);

  Collection<String> getRelationMembers(String identityId, String relationType);

  Collection<String> getRelationTypes(String identityId);

  boolean relationExists(String sourceIdentityId, String relationType, String destIdentityId);

  String getRelationId(String identityId, String relationType);

  /**
   * Get all relationIds where identityId is a member.
   */
  Collection<String> getRelationIdsForIdentity(String identityId);
}
