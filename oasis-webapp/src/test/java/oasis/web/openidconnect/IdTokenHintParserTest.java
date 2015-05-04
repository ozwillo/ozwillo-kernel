/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import java.security.KeyPair;

import org.assertj.core.api.Assertions;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.junit.Before;
import org.junit.Test;

import oasis.security.KeyPairLoader;
import oasis.web.authz.KeysEndpoint;

public class IdTokenHintParserTest {

  private static final String ISSUER = "https://issuer.org";
  private static final String SERVICE_PROVIDER = "service provider";

  private KeyPair keyPair;

  @Before public void setUp() {
    keyPair = KeyPairLoader.generateRandomKeyPair();
  }

  @Test
  public void testValidIdTokenHint() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setSubject("accountId");
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    JwtClaims parsedClaims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(parsedClaims).isNotNull();
  }

  @Test
  public void testBadIdTokenHint() throws Throwable {
    JwtClaims claims = IdTokenHintParser.parseIdTokenHint("invalid id_token_hint", keyPair.getPublic(), ISSUER,
        "accountId");

    Assertions.assertThat(claims).isNull();
  }

  @Test public void testParseIdTokenHint_badSignature() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setSubject("accountId");
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(KeyPairLoader.generateRandomKeyPair().getPrivate());
    String idToken = jws.getCompactSerialization();

    JwtClaims parsedClaims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(parsedClaims).isNull();
  }

  @Test public void testParseIdTokenHint_badIssuer() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer("https://example.com");
    claims.setSubject("accountId");
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    JwtClaims parsedClaims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(parsedClaims).isNull();
  }

  @Test public void testParseIdTokenHint_badSubject() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setSubject("foo");
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    JwtClaims parsedClaims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(parsedClaims).isNull();
  }

  @Test public void testParseIdTokenHint_subjectIgnoredIfNoExpectedSubject() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setSubject("foo");
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    JwtClaims parsedClaims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, null);

    Assertions.assertThat(parsedClaims).isNotNull();
  }

  @Test public void testParseIdTokenHintGetSubject_missingSubject() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setAudience(SERVICE_PROVIDER);
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    String subject = IdTokenHintParser.parseIdTokenHintGetSubject(idToken, keyPair.getPublic(), ISSUER);

    Assertions.assertThat(subject).isNull();
  }

  @Test public void testParseIdTokenHintGetAudience_missingAudience() throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setSubject("accountId");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();


    String audience = IdTokenHintParser.parseIdTokenHintGetAudience(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(audience).isNull();
  }
}
