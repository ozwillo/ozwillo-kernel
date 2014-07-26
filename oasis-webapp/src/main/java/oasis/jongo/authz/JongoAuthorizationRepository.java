package oasis.jongo.authz;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import oasis.jongo.JongoBootstrapper;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;

public class JongoAuthorizationRepository implements AuthorizationRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JongoAuthorizationRepository.class);

  private final Jongo jongo;

  @Inject JongoAuthorizationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public AuthorizedScopes getAuthorizedScopes(String accountId, String clientId) {
    return getAuthorizedScopesCollection()
        .findOne("{ account_id: #, client_id: # }", accountId, clientId)
        .as(AuthorizedScopes.class);
  }

  @Override
  public AuthorizedScopes authorize(String accountId, String clientId, Collection<String> scopeIds) {
    return getAuthorizedScopesCollection()
        .findAndModify("{ account_id: #, client_id: # }", accountId, clientId)
        .upsert()
        .with("{ $addToSet: { scope_ids: { $each: # } } }", ImmutableSet.copyOf(scopeIds))
        .returnNew()
        .as(AuthorizedScopes.class);
  }

  @Override
  public boolean revoke(String accountId, String clientId) {
    int n = getAuthorizedScopesCollection()
        .remove("{ account_id: #, client_id: # }", accountId, clientId)
        .getN();
    if (n > 1) {
      logger.error("Deleted {} authorizations for account_id {} and client_id {}, that shouldn't have happened.", n);
    }
    return n > 0;
  }

  private MongoCollection getAuthorizedScopesCollection() {
    return jongo.getCollection("authorized_scopes");
  }

  @Override
  public void bootstrap() {
    getAuthorizedScopesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getAuthorizedScopesCollection().ensureIndex("{ account_id: 1, client_id: 1 }", "{ unique: 1 }");
  }
}
