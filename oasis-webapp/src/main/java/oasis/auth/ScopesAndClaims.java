/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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
package oasis.auth;

import java.util.Set;
import java.util.stream.Stream;

import org.immutables.value.Value;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import oasis.model.authz.Scopes;

@Value.Immutable
public abstract class ScopesAndClaims {
  public static ScopesAndClaims of(Set<String> scopeIds, Set<String> claimNames) {
    if (scopeIds.stream().anyMatch(Scopes.SCOPES_TO_CLAIMS::containsKey)) {
      // Map scopes to claims, and remove the mapped scopes
      claimNames = Stream.concat(Scopes.mapScopesToClaims(scopeIds), claimNames.stream())
          .collect(ImmutableSet.toImmutableSet());
      scopeIds = scopeIds.stream()
          .filter(scopeId -> !Scopes.SCOPES_TO_CLAIMS.containsKey(scopeId))
          .collect(ImmutableSet.toImmutableSet());
    }
    return ImmutableScopesAndClaims.of(ImmutableSet.copyOf(scopeIds), ImmutableSet.copyOf(claimNames));
  }

  public static ScopesAndClaims of() {
    return of(ImmutableSet.of(), ImmutableSet.of());
  }

  @Value.Parameter
  public abstract ImmutableSet<String> getScopeIds();

  @Value.Parameter
  public abstract ImmutableSet<String> getClaimNames();

  public boolean containsAll(ScopesAndClaims other) {
    return getScopeIds().containsAll(other.getScopeIds())
        && getClaimNames().containsAll(other.getClaimNames());
  }

  public ScopesAndClaims union(ScopesAndClaims other) {
    return ImmutableScopesAndClaims.of(
        Sets.union(getScopeIds(), other.getScopeIds()).immutableCopy(),
        Sets.union(getClaimNames(), other.getClaimNames()).immutableCopy());
  }
}
