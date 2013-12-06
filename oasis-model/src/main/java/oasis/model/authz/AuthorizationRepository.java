package oasis.model.authz;

import java.util.Collection;

import oasis.model.applications.Scope;

public interface AuthorizationRepository {
  Scope getScope(String scopeId);

  Iterable<Scope> getScopes(Collection<String> scopeIds);

  AuthorizedScopes getAuthorizedScopes(String accountId, String serviceProviderId);

  void authorize(String accountId, String serviceProviderId, Collection<String> scopesId);

  boolean revoke(String accountId, String serviceProviderId);
}
