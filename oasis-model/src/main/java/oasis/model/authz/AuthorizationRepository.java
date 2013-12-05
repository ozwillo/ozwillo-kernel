package oasis.model.authz;

import java.util.Collection;

import oasis.model.applications.Scope;

public interface AuthorizationRepository {
  public Scope getScope(String scopeId);

  public Iterable<Scope> getScopes(Collection<String> scopeIds);

  public AuthorizedScopes getAuthorizedScopes(String accountId, String serviceProviderId);

  public void authorize(String accountId, String serviceProviderId, Collection<String> scopesId);

  public boolean revoke(String accountId, String serviceProviderId);
}
