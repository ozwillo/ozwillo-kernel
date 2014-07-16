package oasis.model.applications.v2;

import java.util.Collection;

public interface ScopeRepository {
  Scope getScope(String scopeId);

  Iterable<Scope> getScopes(Collection<String> scopeIds);

  Iterable<Scope> getScopesOfAppInstance(String instanceId);

  Scope createOrUpdateScope(Scope scope);

  int deleteScopesOfAppInstances(Iterable<String> instanceIds);

  int deleteScopesOfAppInstance(String instanceId);

  int deleteOtherScopesOfAppInstance(String instanceId, Iterable<String> localScopeIdsToKeep);
}
