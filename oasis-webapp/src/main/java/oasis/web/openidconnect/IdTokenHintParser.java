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
package oasis.web.openidconnect;

import static org.jose4j.jwa.AlgorithmConstraints.ConstraintType.WHITELIST;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.InvalidJwtSignatureException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.jwt.consumer.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;

public class IdTokenHintParser {
  private static final Logger logger = LoggerFactory.getLogger(IdTokenHintParser.class);

  public static @Nullable String parseIdTokenHintGetAudience(String idTokenHint, PublicKey publicKey, String expectedIssuer, @Nullable final String expectedSubject) {
    JwtClaims claims = parseIdTokenHint(idTokenHint, publicKey, expectedIssuer, expectedSubject);
    if (claims == null) {
      return null;
    }
    List<String> audience;
    try {
      audience = claims.getAudience();
    } catch (MalformedClaimException e) {
      return null;
    }
    if (audience == null || audience.isEmpty()) {
      return null;
    }
    return audience.get(0);
  }

  public static @Nullable String parseIdTokenHintGetSubject(String idTokenHint, PublicKey publicKey, String expectedIssuer) {
    JwtClaims claims = parseIdTokenHint(idTokenHint, publicKey, expectedIssuer, null);
    if (claims == null) {
      return null;
    }
    try {
      return claims.getSubject();
    } catch (MalformedClaimException e) {
      return null;
    }
  }

  @VisibleForTesting
  static @Nullable JwtClaims parseIdTokenHint(String idTokenHint, PublicKey publicKey, String expectedIssuer, @Nullable final String expectedSubject) {
    try {
      return new JwtConsumerBuilder()
          .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
          .setVerificationKey(publicKey)
          .setExpectedIssuer(expectedIssuer)
          .setSkipDefaultAudienceValidation()
          .setAllowedClockSkewInSeconds(Integer.MAX_VALUE)  // We don't want to validate the time
          .setExpectedSubject(expectedSubject)              // setExpectedSubject calls setRequireSubject even if expectedSubject is null
          .build()
          .processToClaims(idTokenHint);
    } catch (InvalidJwtSignatureException e) {
      logger.debug("Bad signature for id_token_hint: {}", idTokenHint);
      return null;
    } catch (InvalidJwtException e) {
      logger.debug("Invalid id_token_hint: {}", idTokenHint, e);
      return null;
    }
  }
}
