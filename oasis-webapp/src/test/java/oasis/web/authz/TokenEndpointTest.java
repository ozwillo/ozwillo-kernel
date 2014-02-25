package oasis.web.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provides;

import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.security.KeyPairLoader;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenInfo;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class TokenEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(TokenEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);

      bind(Clock.class).to(FixedClock.class);
      bind(JsonFactory.class).to(JacksonFactory.class);

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder()
          .setIdTokenDuration(Duration.standardMinutes(1))
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());
    }

    @Provides @TestSingleton FixedClock providesFixedClock() {
      return new FixedClock(now.getMillis());
    }
  }

  static final Instant now = new DateTime(2014, 7, 17, 14, 30).toInstant();
  static final Instant tomorrow = now.plus(Duration.standardDays(1));

  static final AuthorizationCode validAuthCode = new AuthorizationCode() {{
    setId("validAuthCode");
    setAccountId("account");
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setServiceProviderId("sp");
    setScopeIds(ImmutableSet.of("dp1s1", "dp1s3", "dp3s1"));
    setRedirectUri("http://sp.example.com/callback");
    setNonce("nonce");
  }};
  static final AuthorizationCode validAuthCodeWithOfflineAccess = new AuthorizationCode() {{
    setId("validAuthCodeWithOfflineAccess");
    setAccountId("account");
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setServiceProviderId("sp");
    setScopeIds(ImmutableSet.of("offline_access", "dp1s1", "dp1s3", "dp3s1"));
    setRedirectUri("http://sp.example.com/callback");
    setNonce("nonce");
  }};

  static final AccessToken accessToken = new AccessToken() {{
    setId("accessToken");
    setAccountId("account");
    setCreationTime(now);
    expiresIn(Duration.standardDays(1));
    setServiceProviderId(validAuthCode.getServiceProviderId());
    setScopeIds(validAuthCode.getScopeIds());
  }};
  static final RefreshToken refreshToken = new RefreshToken() {{
    setId("refreshToken");
    setAccountId("account");
    setCreationTime(now);
    expiresIn(Duration.standardDays(100));
    setServiceProviderId(validAuthCode.getServiceProviderId());
    setScopeIds(validAuthCodeWithOfflineAccess.getScopeIds());
  }};
  static final AccessToken accessTokenWithOfflineAccess = new AccessToken() {{
    setId("accessTokenWithOfflineAccess");
    setAccountId("account");
    setCreationTime(now);
    expiresIn(Duration.standardDays(1));
    setServiceProviderId(refreshToken.getServiceProviderId());
    setScopeIds(refreshToken.getScopeIds());
  }};
  static final AccessToken refreshedAccessToken = new AccessToken() {{
    setId("refreshedAccessToken");
    setAccountId("account");
    setCreationTime(tomorrow);
    expiresIn(Duration.standardDays(1));
    setServiceProviderId(refreshToken.getServiceProviderId());
    setScopeIds(ImmutableSet.of("dp1s1", "dp3s1"));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", AuthorizationCode.class)).thenReturn(validAuthCode);
    when(tokenHandler.getCheckedToken("offline", AuthorizationCode.class)).thenReturn(validAuthCodeWithOfflineAccess);
    when(tokenHandler.getCheckedToken("invalid", AuthorizationCode.class)).thenReturn(null);
    when(tokenHandler.getCheckedToken("valid", RefreshToken.class)).thenReturn(refreshToken);
    when(tokenHandler.getCheckedToken("invalid", RefreshToken.class)).thenReturn(null);

    when(tokenHandler.createAccessToken(validAuthCode)).thenReturn(accessToken);
    when(tokenHandler.createRefreshToken(validAuthCodeWithOfflineAccess)).thenReturn(refreshToken);
    when(tokenHandler.createAccessToken(refreshToken)).thenReturn(accessTokenWithOfflineAccess);
    when(tokenHandler.createAccessToken(refreshToken, refreshedAccessToken.getScopeIds()))
        .thenReturn(refreshedAccessToken);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(TokenEndpoint.class);
  }

  @Test public void testUnsupportedGrantType(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = resteasy.getClient().target(UriBuilder.fromResource(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form("grant_type", "unsupported")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("unsupported_grant_type");
  }

  @Test public void testMissingGrantType(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = resteasy.getClient().target(UriBuilder.fromResource(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form("code", "valid")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getErrorDescription()).contains("grant_type");
  }

  @Test public void testDuplicateGrantType(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = resteasy.getClient().target(UriBuilder.fromResource(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("grant_type", "authorization_code")
            .param("code", "valid")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getErrorDescription()).contains("grant_type");
  }

  @Test public void testInvalidAuthCode(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCode("invalid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testAuthCodeMismatchingRedirectUri(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCode("valid", "http://whatever.example.net");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getErrorDescription()).contains("redirect_uri");
  }

  @Test public void testStolenAuthCode(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp2"));

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testValidAuthCode(JsonFactory jsonFactory, OpenIdConnectModule.Settings settings, Clock clock) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessToken));
    assertThat(response.getRefreshToken()).isNullOrEmpty();

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo("account");
    assertThat(payload.getAudience()).isEqualTo("sp");
    assertThat(payload.getIssuedAtTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()));
    assertThat(payload.getExpirationTimeSeconds()).isEqualTo(payload.getIssuedAtTimeSeconds() + settings.idTokenDuration.getStandardSeconds());
    assertThat(payload.getNonce()).isEqualTo("nonce");
  }

  @Test public void testValidAuthCodeWithOfflineAccess(JsonFactory jsonFactory, OpenIdConnectModule.Settings settings, Clock clock) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCode("offline", validAuthCodeWithOfflineAccess.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("offline_access", "dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessTokenWithOfflineAccess.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessTokenWithOfflineAccess));
    assertThat(response.getRefreshToken()).isEqualTo(TokenSerializer.serialize(refreshToken));

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo("account");
    assertThat(payload.getAudience()).isEqualTo("sp");
    assertThat(payload.getIssuedAtTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()));
    assertThat(payload.getExpirationTimeSeconds()).isEqualTo(payload.getIssuedAtTimeSeconds() + settings.idTokenDuration.getStandardSeconds());
    assertThat(payload.getNonce()).isEqualTo("nonce");
  }

  private Response authCode(String authorizationCode, String redirectUri) {
    return resteasy.getClient().target(UriBuilder.fromResource(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("code", authorizationCode)
            .param("redirect_uri", redirectUri)));
  }

  @Test public void testInvalidRefreshToken(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = refreshToken("invalid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testRefreshTokenMismatchingScopes(JsonFactory jsonFactory, FixedClock clock) throws Throwable {
    // given
    clock.setTime(tomorrow.getMillis());
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = refreshToken("valid", "dp1s1 unknown dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_scope");
  }

  @Test public void testStolenRefreshToken(JsonFactory jsonFactory, FixedClock clock) throws Throwable {
    // given
    clock.setTime(tomorrow.getMillis());
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp2"));

    // when
    Response resp = refreshToken("valid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testValidRefreshToken(JsonFactory jsonFactory, FixedClock clock) throws Throwable {
    // given
    clock.setTime(tomorrow.getMillis());
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = refreshToken("valid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, TokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), refreshedAccessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(refreshedAccessToken));
    assertThat(response.getRefreshToken()).isNullOrEmpty();
  }

  private Response refreshToken(String refreshToken, String scope) {
    return resteasy.getClient().target(UriBuilder.fromResource(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "refresh_token")
            .param("refresh_token", refreshToken)
            .param("scope", scope)));
  }
}
