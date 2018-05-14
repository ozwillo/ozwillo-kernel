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
package oasis.model.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ScopesTest {

  @Test public void testMapScopesToClaims() {
    assertThat(mapScopesToClaims()).isEmpty();

    assertThat(mapScopesToClaims(Scopes.OPENID)).isEmpty();

    Scopes.SCOPES_TO_CLAIMS.keySet().forEach(scopeId ->
        assertThat(mapScopesToClaims(scopeId)).containsExactlyInAnyOrder(Scopes.SCOPES_TO_CLAIMS.get(scopeId).toArray(new String[0]))
    );

    assertThat(mapScopesToClaims(Scopes.PROFILE, Scopes.PROFILE))
        .as("Gives no guarantee about uniqueness")
        .size().isEqualTo(2 * Scopes.SCOPES_TO_CLAIMS.get(Scopes.PROFILE).size());

    assertThat(mapScopesToClaims("foo", "bar")).as("Ignore 'unknown' scopes").isEmpty();
    assertThat(mapScopesToClaims("foo", Scopes.PROFILE, "bar", Scopes.PHONE, "baz"))
        .as("Ignore 'unknown' scopes")
        .size().isEqualTo(Scopes.SCOPES_TO_CLAIMS.get(Scopes.PROFILE).size() + Scopes.SCOPES_TO_CLAIMS.get(Scopes.PHONE).size());
  }

  private ImmutableList<String> mapScopesToClaims(String... scopeIds) {
    return Scopes.mapScopesToClaims(Arrays.asList(scopeIds)).collect(ImmutableList.toImmutableList());
  }
}
