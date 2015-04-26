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
import org.jose4j.jwt.JwtClaims;
import org.junit.Before;
import org.junit.Test;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import oasis.security.KeyPairLoader;
import oasis.web.authz.KeysEndpoint;

public class IdTokenHintParserTest {

  private static final String ISSUER = "https://issuer.org";
  private static final String SERVICE_PROVIDER = "service provider";

  private KeyPair keyPair;
  private JsonFactory jsonFactory = new JacksonFactory();

  @Before public void setUp() {
    keyPair = KeyPairLoader.generateRandomKeyPair();
  }

  @Test
  public void testValidIdTokenHint() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject("accountId")
            .setAudience(SERVICE_PROVIDER)
    );

    JwtClaims claims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(claims).isNotNull();
  }

  @Test
  public void testBadIdTokenHint() throws Throwable {
    JwtClaims claims = IdTokenHintParser.parseIdTokenHint("invalid id_token_hint", keyPair.getPublic(), ISSUER,
        "accountId");

    Assertions.assertThat(claims).isNull();
  }

  @Test public void testParseIdTokenHint_badSignature() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(
        KeyPairLoader.generateRandomKeyPair().getPrivate(),
        jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject("accountId")
            .setAudience(SERVICE_PROVIDER)
    );

    JwtClaims claims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(claims).isNull();
  }

  @Test public void testParseIdTokenHint_badIssuer() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer("https://example.com")
            .setSubject("accountId")
            .setAudience(SERVICE_PROVIDER)
    );

    JwtClaims claims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(claims).isNull();
  }

  @Test public void testParseIdTokenHint_badSubject() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject("foo")
            .setAudience(SERVICE_PROVIDER)
    );

    JwtClaims claims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(claims).isNull();
  }

  @Test public void testParseIdTokenHint_subjectIgnoredIfNoExpectedSubject() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject("foo")
            .setAudience(SERVICE_PROVIDER)
    );

    JwtClaims claims = IdTokenHintParser.parseIdTokenHint(idToken, keyPair.getPublic(), ISSUER, null);

    Assertions.assertThat(claims).isNotNull();
  }

  @Test public void testParseIdTokenHintGetSubject_missingSubject() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject(null)
            .setAudience(SERVICE_PROVIDER)
    );

    String subject = IdTokenHintParser.parseIdTokenHintGetSubject(idToken, keyPair.getPublic(), ISSUER);

    Assertions.assertThat(subject).isNull();
  }

  @Test public void testParseIdTokenHintGetAudience_missingAudience() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject("accountId")
            .setAudience(null)
    );

    String audience = IdTokenHintParser.parseIdTokenHintGetAudience(idToken, keyPair.getPublic(), ISSUER, "accountId");

    Assertions.assertThat(audience).isNull();
  }
}
