package oasis.services.authorizations;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mongodb.WriteResult;

import oasis.model.accounts.Account;
import oasis.model.applications.Scope;
import oasis.model.authorizations.AuthorizationRepository;
import oasis.model.authorizations.AuthorizedScopes;

public class JongoAuthorizationRepository implements AuthorizationRepository {
  private static final Logger logger = LoggerFactory.getLogger(JongoAuthorizationRepository.class);

  private final Jongo jongo;

  @Inject JongoAuthorizationRepository(Jongo jongo) {
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
    Account account = getAccountCollection().findOne("{id: #, authorizedScopes.serviceProviderId: #}", accountId, serviceProviderId)
        .projection("{type: 1, authorizedScopes.$: 1}").as(Account.class);
    if (account == null || account.getAuthorizedScopes() == null || account.getAuthorizedScopes().isEmpty()) {
      return null;
    }

    return account.getAuthorizedScopes().get(0);
  }

  @Override
  public void authorize(String accountId, String serviceProviderId, Collection<String> scopesId) {
    // Try to add new authorized scopes to an account for a service provider
    WriteResult writeResult = getAccountCollection().update("{id: #, authorizedScopes.serviceProviderId: #}", accountId, serviceProviderId)
        .with("{$addToSet: {authorizedScopes.$.scopeIds: {$each : #}}}", ImmutableList.copyOf(scopesId));

    int n = writeResult.getN();
    if (n > 1) {
      logger.error("Inserted {} authorizations for accountId {} and serviceProviderId {}, that shouldn't have happened.");
    } else if (n == 0) {
      // It seems that the account didn't already have given authorizations to the service provider
      AuthorizedScopes authorizedScopes = new AuthorizedScopes();
      authorizedScopes.setServiceProviderId(serviceProviderId);
      authorizedScopes.setScopeIds(ImmutableSet.copyOf(scopesId));

      writeResult = getAccountCollection().update("{id: #}", accountId).with("{$push: {authorizedScopes: #}}", authorizedScopes);
      if (writeResult.getN() == 0) {
        logger.warn("Can't add authorized scopes to the account {}.", accountId);
      }
    }
  }

  @Override
  public boolean revoke(String accountId, String serviceProviderId) {
    WriteResult writeResult = getAccountCollection().update("{id: #, authorizedScopes.serviceProviderId: #}", accountId, serviceProviderId)
        .with("{$pull: {authorizedScopes: {serviceProviderId: #}}}", serviceProviderId);

    int n = writeResult.getN();
    if (n > 1) {
      logger.error("Deleted {} authorizations for accountId {} and serviceProviderId {}, that shouldn't have happened.");
    }
    return n > 0;
  }

  private MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  private MongoCollection getScopesCollection() {
    return jongo.getCollection("scopes");
  }
}
