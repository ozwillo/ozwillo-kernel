package oasis.services.authorizations;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import oasis.model.applications.Scope;
import oasis.model.authorizations.AuthorizationRepository;
import oasis.model.authorizations.AuthorizedScopes;

public class JongoAuthorizationRepository implements AuthorizationRepository {

  private final Jongo jongo;

  @Inject
  JongoAuthorizationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Scope getScope(String scopeId) {
    return getScopesCollection().findOne("{id:#}", scopeId).as(Scope.class);
  }

  @Override
  public Iterable<Scope> getScopes(Collection<String> scopeIds) {
    // TODO: Improve performance by merging all requests into one request
    // XXX: What should be the behavior of this request if one of the scope id is not present in the database ?
    // XXX: Should we throw an error, return an empty iterable or just ignore it and return all found scopes ?
    ImmutableList.Builder<Scope> builder = ImmutableList.builder();
    for (String scopeId : scopeIds) {
      Scope scope = getScope(scopeId);
      if (scope == null) {
        throw new IllegalArgumentException("The scope " + scopeId + " does not exist.");
      }
      builder.add(scope);
    }
    return builder.build();
  }

  @Override
  public AuthorizedScopes getAuthorizedScopes(String accountId, String serviceProviderId) {
    return getAuthorizedScopesCollection().findOne("{userAccountId:#, serviceProviderId:#}", accountId, serviceProviderId).as(AuthorizedScopes.class);
  }

  @Override
  public void authorize(String accountId, String serviceProviderId, Collection<String> scopesId) {
    AuthorizedScopes authorizedScopes = new AuthorizedScopes();
    authorizedScopes.setAccountId(accountId);
    authorizedScopes.setServiceProviderId(serviceProviderId);
    authorizedScopes.setScopeIds(Sets.newHashSet(scopesId));
    getAuthorizedScopesCollection()
        .update("{userAccountId:#, serviceProviderId:#}", accountId, serviceProviderId)
        .upsert().merge(authorizedScopes);
  }

  @Override
  public void revoke(String accountId, String serviceProviderId) {
    getAuthorizedScopesCollection().remove("{userAccountId:#, serviceProviderId:#}", accountId, serviceProviderId);
  }

  private MongoCollection getAuthorizedScopesCollection() {
    return jongo.getCollection("grants");
  }

  private MongoCollection getScopesCollection() {
    return jongo.getCollection("scopes");
  }
}
