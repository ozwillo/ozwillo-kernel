package oasis.model.authz;

import java.util.Collection;

public interface AuthorizationRepository {
  AuthorizedScopes getAuthorizedScopes(String accountId, String serviceProviderId);

  void authorize(String accountId, String serviceProviderId, Collection<String> scopesId);

  boolean revoke(String accountId, String serviceProviderId);
}
