package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;

import java.security.KeyPair;

import org.junit.Before;
import org.junit.Test;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import oasis.model.authn.SidToken;
import oasis.security.KeyPairLoader;
import oasis.web.authz.KeysEndpoint;

public class LogoutPageParseIdTokenHintTest {

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
    setAccountId("accountId");
  }};

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
            .setSubject(sidToken.getAccountId())
            .setAudience(SERVICE_PROVIDER)
    );

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, sidToken);

    assertThat(idTokenHint).isNotNull();
  }

  @Test
  public void testBadIdTokenHint() throws Throwable {
    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, "invalid id_token_hint", sidToken);

    assertThat(idTokenHint).isNull();
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
            .setSubject(sidToken.getAccountId())
            .setAudience(SERVICE_PROVIDER)
    );

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, sidToken);

    assertThat(idTokenHint).isNull();
  }

  @Test public void testParseIdTokenHint_badIssuer() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer("https://example.com")
            .setSubject(sidToken.getAccountId())
            .setAudience(SERVICE_PROVIDER)
    );

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, sidToken);

    assertThat(idTokenHint).isNull();
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

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, sidToken);

    assertThat(idTokenHint).isNull();
  }

  @Test public void testParseIdTokenHint_subjectIgnoredIfNoSidToken() throws Throwable {
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

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, null);

    assertThat(idTokenHint).isNotNull();
  }

  @Test public void testParseIdTokenHint_badAudience() throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(ISSUER)
            .setSubject(sidToken.getAccountId())
            .setAudience(null)
    );

    IdToken.Payload idTokenHint = LogoutPage.parseIdTokenHint(jsonFactory, keyPair.getPublic(), ISSUER, idToken, sidToken);

    assertThat(idTokenHint).isNull();
  }
}
