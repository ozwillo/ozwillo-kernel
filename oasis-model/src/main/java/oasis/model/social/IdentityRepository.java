package oasis.model.social;

public interface IdentityRepository {
  Identity getIdentity(String identityId);

  Identity createIdentity(Identity identity);

  boolean updateIdentity(Identity identity);

  boolean deleteIdentity(String identityId);
}
