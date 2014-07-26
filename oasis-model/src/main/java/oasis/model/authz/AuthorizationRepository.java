package oasis.model.authz;

import java.util.Collection;

public interface AuthorizationRepository {
  AuthorizedScopes getAuthorizedScopes(String accountId, String clientId);

  AuthorizedScopes authorize(String accountId, String clientId, Collection<String> scopeIds);

  boolean revoke(String accountId, String clientId);
}
