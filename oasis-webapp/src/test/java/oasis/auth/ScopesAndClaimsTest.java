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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import oasis.model.authz.Scopes;

public class ScopesAndClaimsTest {

  @Test public void testFactory() {
    assertThat(ScopesAndClaims.of()).isEqualTo(ScopesAndClaims.of(ImmutableSet.of(), ImmutableSet.of()));

    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")))
        .as("Keeps 'unknown' values as-is")
        .returns(ImmutableSet.of("foo", "bar"), ScopesAndClaims::getScopeIds)
        .returns(ImmutableSet.of("baz", "qux"), ScopesAndClaims::getClaimNames);

    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("foo", "bar")))
        .as("Scopes and claims can share ids/names")
        .returns(ImmutableSet.of("foo", "bar"), ScopesAndClaims::getScopeIds)
        .returns(ImmutableSet.of("foo", "bar"), ScopesAndClaims::getClaimNames);

    assertThat(ScopesAndClaims.of(ImmutableSet.of(Scopes.OPENID, "foo", Scopes.PROFILE), ImmutableSet.of("bar", "baz")))
        .as("Transforms scopes to claims, and filters them out from scopes")
        .returns(ImmutableSet.of("foo"), ScopesAndClaims::getScopeIds)
        .returns(Sets.union(Scopes.SCOPES_TO_CLAIMS.get(Scopes.PROFILE), ImmutableSet.of("bar", "baz")).immutableCopy(), ScopesAndClaims::getClaimNames);
  }

  @Test public void testContainsAll() {
    assertThat(ScopesAndClaims.of().containsAll(ScopesAndClaims.of())).as("Empty contains empty").isTrue();
    assertThat(ScopesAndClaims.of().containsAll(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of())))
        .as("Empty does not contain non-empty (scopes)")
        .isFalse();
    assertThat(ScopesAndClaims.of().containsAll(ScopesAndClaims.of(ImmutableSet.of(), ImmutableSet.of("foo"))))
        .as("Empty does not contain non-empty (claims)")
        .isFalse();
    assertThat(ScopesAndClaims.of().containsAll(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar"))))
        .as("Empty does not contain non-empty (scopes and claims)")
        .isFalse();

    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of()))
        .as("Non-empty contains empty")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of())))
        .as("Non-empty contains same scopes, empty claims")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of())))
        .as("Non-empty contains scopes subset, empty claims")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of(), ImmutableSet.of("baz", "qux"))))
        .as("Non-empty contains empty scopes, same claims")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of(), ImmutableSet.of("baz"))))
        .as("Non-empty contains empty scopes, claims subset")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux"))))
        .as("Non-empty contains same scopes, same claims")
        .isTrue();
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo", "bar"), ImmutableSet.of("baz", "qux")).containsAll(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("baz"))))
        .as("Non-empty contains subsets")
        .isTrue();
  }

  @Test public void testUnion() {
    assertThat(ScopesAndClaims.of().union(ScopesAndClaims.of()))
        .as("Empty union empty is empty")
        .isEqualTo(ScopesAndClaims.of());
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")).union(ScopesAndClaims.of()))
        .as("Non-empty union empty is equal")
        .isEqualTo(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")));
    assertThat(ScopesAndClaims.of().union(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar"))))
        .as("Empty union non-empty is equal to non-empty")
        .isEqualTo(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")));
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")).union(ScopesAndClaims.of(ImmutableSet.of("baz"), ImmutableSet.of("qux"))))
        .as("Non-empty union non-empty")
        .isEqualTo(ScopesAndClaims.of(ImmutableSet.of("foo", "baz"), ImmutableSet.of("bar", "qux")));
    assertThat(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")).union(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar"))))
        .as("Non-empty union equal empty is equal")
        .isEqualTo(ScopesAndClaims.of(ImmutableSet.of("foo"), ImmutableSet.of("bar")));
  }
}
