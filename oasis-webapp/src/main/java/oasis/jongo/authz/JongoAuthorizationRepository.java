/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.jongo.authz;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import oasis.jongo.JongoBootstrapper;
import oasis.jongo.OasisIdHelper;
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
  public AuthorizedScopes authorize(String accountId, String clientId, Collection<String> scopeIds, Collection<String> claimNames) {
    return getAuthorizedScopesCollection()
        .findAndModify("{ account_id: #, client_id: # }", accountId, clientId)
        .upsert()
        .with("{ $addToSet: { scope_ids: { $each: # }, claim_names: { $each: # } }, $setOnInsert: { id: # } }",
            ImmutableSet.copyOf(scopeIds), ImmutableSet.copyOf(claimNames), OasisIdHelper.generateId())
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

  @Override
  public int revokeAllForClient(String clientId) {
    return getAuthorizedScopesCollection()
        .remove("{ client_id: # }", clientId)
        .getN();
  }

  @Override
  public int revokeForAllUsers(Collection<String> scopeIds) {
    if (scopeIds.isEmpty()) {
      return 0;
    }
    scopeIds = ImmutableSet.copyOf(scopeIds);
    return getAuthorizedScopesCollection()
        .update("{ scope_ids: { $in: # } }", scopeIds)
        .multi()
        .with("{ $pullAll: { scope_ids: # } }", scopeIds)
        .getN();
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
