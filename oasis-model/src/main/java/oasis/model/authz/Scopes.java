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
package oasis.model.authz;

import java.util.Objects;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

public interface Scopes {
  // OpenID Connect 1.0
  public static final String OPENID = "openid";
  public static final String PROFILE = "profile";
  public static final String EMAIL = "email";
  public static final String ADDRESS = "address";
  public static final String PHONE = "phone";
  public static final String OFFLINE_ACCESS = "offline_access";

  public static final ImmutableMap<String, ImmutableSet<String>> SCOPES_TO_CLAIMS = ImmutableMap.of(
      OPENID, ImmutableSet.of(), // XXX: do not record the "sub" claim; this scope is required anyway
      PROFILE, ImmutableSet.of(
          "name", "family_name", "given_name", "middle_name", "nickname", "preferred_username",
          "profile", "picture", "website",
          "gender", "birthdate",
          "zoneinfo", "locale",
          "updated_at"),
      EMAIL, ImmutableSet.of("email", "email_verified"),
      ADDRESS, ImmutableSet.of("address"),
      PHONE, ImmutableSet.of("phone_number", "phone_number_verified")
  );
  public static final ImmutableSet<String> SUPPORTED_CLAIMS = SCOPES_TO_CLAIMS.values().stream()
      .flatMap(ImmutableSet::stream)
      .collect(ImmutableSet.toImmutableSet());

  public static Stream<String> mapScopesToClaims(Iterable<String> scopeIds) {
    return Streams.stream(scopeIds)
        .map(SCOPES_TO_CLAIMS::get)
        .filter(Objects::nonNull)
        .flatMap(ImmutableSet::stream);
  }
}
