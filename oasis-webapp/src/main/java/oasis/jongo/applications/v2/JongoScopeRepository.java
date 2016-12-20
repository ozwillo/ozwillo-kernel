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
package oasis.jongo.applications.v2;

import java.util.Collection;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.collect.ImmutableList;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;

public class JongoScopeRepository implements ScopeRepository, JongoBootstrapper {
  private final Jongo jongo;

  @Inject JongoScopeRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getScopesCollection() {
    return jongo.getCollection("scopes");
  }

  @Override
  public Scope getScope(String scopeId) {
    return getScopesCollection()
        .findOne("{ id: # }", scopeId)
        .as(Scope.class);
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
  public Iterable<Scope> getScopesOfAppInstance(String instanceId) {
    return getScopesCollection()
        .find("{ instance_id: # }", instanceId)
        .as(Scope.class);
  }

  @Override
  public Scope createOrUpdateScope(Scope scope) {
    getScopesCollection()
        .update("{ id: # }", scope.getId())
        .upsert()
        .with(scope);
    return scope;
  }

  @Override
  public int deleteScopesOfAppInstances(Iterable<String> instanceIds) {
    return getScopesCollection().remove("{ instance_id: { $in: # } }", instanceIds).getN();
  }

  @Override
  public int deleteScopesOfAppInstance(String instanceId) {
    return getScopesCollection().remove("{ instance_id: # }", instanceId).getN();
  }

  @Override
  public int deleteOtherScopesOfAppInstance(String instanceId, Iterable<String> localScopeIdsToKeep) {
    return getScopesCollection()
        .remove("{ instance_id: #, local_id: { $nin: # } }", instanceId, localScopeIdsToKeep)
        .getN();
  }

  @Override
  public void bootstrap() {
    getScopesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getScopesCollection().ensureIndex("{ instance_id: 1, local_id: 1 }", "{ unique: 1, sparse: 1 }");
  }
}
