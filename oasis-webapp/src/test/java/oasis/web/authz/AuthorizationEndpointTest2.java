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
package oasis.web.authz;

import static oasis.web.authz.AuthorizationEndpoint.mergeClaims;
import static oasis.web.authz.AuthorizationEndpoint.parseClaims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class AuthorizationEndpointTest2 {

  @Test public void testParseClaims() {
    parseClaims("null");
    assertThat(parseClaims("foo"))
        .as("Not JSON")
        .isNull();
    assertThat(parseClaims("{\"userinfo\": {"))
        .as("Broken JSON")
        .isNull();

    assertThat(parseClaims("{\"foo\": { \"name\": null }, \"id_token\": { \"email\": null } }"))
        .as("Ignore unknown top-level keys")
        .isEmpty();

    assertThat(parseClaims("{}"))
        .as("Empty object")
        .isEmpty();
    assertThat(parseClaims("{\"userinfo\": null }"))
        .as("Null userinfo object")
        .isNull();
    assertThat(parseClaims("{\"userinfo\": {} }"))
        .as("Empty userinfo object")
        .isEmpty();

    assertThat(parseClaims("{\"userinfo\": { \"name\": null } }"))
        .as("Null value means voluntary")
        .containsOnly(entry("name", false));
    assertThat(parseClaims("{\"userinfo\": { \"name\": { \"essential\": false } } }"))
        .as("essential:false value means voluntary")
        .containsOnly(entry("name", false));
    assertThat(parseClaims("{\"userinfo\": { \"name\": { \"essential\": true } } }"))
        .as("essential:true value means essential")
        .containsOnly(entry("name", true));
    assertThat(parseClaims("{\"userinfo\": { \"name\": true } }"))
        .as("Non-object non-null claim value")
        .isNull();
    assertThat(parseClaims("{\"userinfo\": { \"name\": { \"essential\": null } } }"))
        .as("Non-boolean essential value")
        .isNull();

    assertThat(parseClaims("{\"userinfo\": { \"name\": { \"value\": \"foo\" } } }"))
        .as("Ignores non-essential claim values")
        .containsOnly(entry("name", false));

    assertThat(parseClaims("{\"userinfo\": { \"http://example.info/claims/groups\": null } }"))
        .as("Ignores unknown claims")
        .isEmpty();

    assertThat(parseClaims(
        "{\n"
            + "   \"userinfo\":\n"
            + "    {\n"
            + "     \"given_name\": {\"essential\": true},\n"
            + "     \"nickname\": null,\n"
            + "     \"email\": {\"essential\": true},\n"
            + "     \"email_verified\": {\"essential\": true},\n"
            + "     \"picture\": null,\n"
            + "     \"http://example.info/claims/groups\": null\n"
            + "    },\n"
            + "   \"id_token\":\n"
            + "    {\n"
            + "     \"auth_time\": {\"essential\": true},\n"
            + "     \"acr\": {\"values\": [\"urn:mace:incommon:iap:silver\"] }\n"
            + "    }\n"
            + "  }"))
        .as("Example from spec")
        .containsExactly(
            entry("given_name", true),
            entry("nickname", false),
            entry("email", true),
            entry("email_verified", true),
            entry("picture", false)
        );
  }

  @Test public void testMergeClaims() {
    assertThat(mergeClaims(ImmutableMap.of(), ImmutableSet.of()))
        .as("Empty inputs")
        .isEmpty();
    assertThat(mergeClaims(ImmutableMap.of(), ImmutableSet.of("name")))
        .as("Empty parsed claims")
        .containsOnly(entry("name", false));
    assertThat(mergeClaims(ImmutableMap.of("name", true), ImmutableSet.of()))
        .as("Empty voluntary claims")
        .containsOnly(entry("name", true));

    assertThat(mergeClaims(ImmutableMap.of("name", true), ImmutableSet.of("email")))
        .as("Distinct claims set")
        .containsOnly(entry("name", true), entry("email", false));
    assertThat(mergeClaims(ImmutableMap.of("name", true), ImmutableSet.of("name")))
        .as("Identical claims set")
        .containsOnly(entry("name", true));
    assertThat(mergeClaims(ImmutableMap.of("name", true, "email", false), ImmutableSet.of("name", "gender")))
        .as("Overlapping claims set")
        .containsOnly(entry("name", true), entry("email", false), entry("gender", false));
  }
}
