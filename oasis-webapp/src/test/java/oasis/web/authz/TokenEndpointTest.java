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
package oasis.web.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provides;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.auth.AuthModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.Scopes;
import oasis.security.KeyPairLoader;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.urls.ImmutableUrls;
import oasis.urls.UrlsModule;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class TokenEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(TokenEndpoint.class);

      install(new NoopAuditLogModule());
      install(new UrlsModule(ImmutableUrls.builder().build()));

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(AppAdminHelper.class).in(TestSingleton.class);

      bind(JsonFactory.class).to(JacksonFactory.class);
      bind(Clock.class).to(FixedClock.class);

      bind(DateTimeUtils.MillisProvider.class).toInstance(new DateTimeUtils.MillisProvider() {
        @Override
        public long getMillis() {
          return now.getMillis();
        }
      });

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
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
  static final Instant oneHourAgo = now.minus(Duration.standardHours(1));

  static final AppInstance appInstance = new AppInstance() {{
    setId("sp");
    setProvider_id("organization");
  }};

  static final UserAccount account = new UserAccount() {{
    setId("account");
  }};

  static final SidToken sidToken = new SidToken() {{
    setId("sidToken");
    setAccountId(account.getId());
    setAuthenticationTime(oneHourAgo);
  }};

  static final AuthorizationCode validAuthCode = new AuthorizationCode() {{
    setId("validAuthCode");
    setAccountId(account.getId());
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setParent(sidToken);
    setServiceProviderId(appInstance.getId());
    setScopeIds(ImmutableSet.of("dp1s1", "dp1s3", "dp3s1"));
    setRedirectUri("http://sp.example.com/callback");
    setNonce("nonce");
  }};
  static final AuthorizationCode validAuthCodeWithOfflineAccess = new AuthorizationCode() {{
    setId("validAuthCodeWithOfflineAccess");
    setAccountId(account.getId());
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setParent(sidToken);
    setServiceProviderId(appInstance.getId());
    setScopeIds(ImmutableSet.of(Scopes.OFFLINE_ACCESS, "dp1s1", "dp1s3", "dp3s1"));
    setRedirectUri("http://sp.example.com/callback");
    setNonce("nonce");
  }};

  static final AccessToken accessToken = new AccessToken() {{
    setId("accessToken");
    setAccountId(account.getId());
    setCreationTime(now);
    expiresIn(Duration.standardDays(1));
    setParent(validAuthCode);
    setServiceProviderId(validAuthCode.getServiceProviderId());
    setScopeIds(validAuthCode.getScopeIds());
  }};
  static final RefreshToken refreshToken = new RefreshToken() {{
    setId("refreshToken");
    setAccountId(account.getId());
    setCreationTime(now);
    expiresIn(Duration.standardDays(100));
    setAncestorIds(ImmutableList.of(validAuthCodeWithOfflineAccess.getId()));
    setServiceProviderId(validAuthCodeWithOfflineAccess.getServiceProviderId());
    setScopeIds(validAuthCodeWithOfflineAccess.getScopeIds());
  }};
  static final AccessToken accessTokenWithOfflineAccess = new AccessToken() {{
    setId("accessTokenWithOfflineAccess");
    setAccountId(account.getId());
    setCreationTime(now);
    expiresIn(Duration.standardDays(1));
    setParent(refreshToken);
    setServiceProviderId(refreshToken.getServiceProviderId());
    setScopeIds(refreshToken.getScopeIds());
  }};
  static final AccessToken refreshedAccessToken = new AccessToken() {{
    setId("refreshedAccessToken");
    setAccountId(account.getId());
    setCreationTime(tomorrow);
    expiresIn(Duration.standardDays(1));
    setParent(refreshToken);
    setServiceProviderId(refreshToken.getServiceProviderId());
    setScopeIds(ImmutableSet.of("dp1s1", "dp3s1"));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler, TokenRepository tokenRepository, AccountRepository accountRepository,
      AppInstanceRepository appInstanceRepository) {
    when(tokenHandler.generateRandom()).thenReturn("pass");

    when(tokenHandler.getCheckedToken("valid", AuthorizationCode.class)).thenReturn(validAuthCode);
    when(tokenHandler.getCheckedToken("offline", AuthorizationCode.class)).thenReturn(validAuthCodeWithOfflineAccess);
    when(tokenHandler.getCheckedToken("invalid", AuthorizationCode.class)).thenReturn(null);
    when(tokenHandler.getCheckedToken("valid", RefreshToken.class)).thenReturn(refreshToken);
    when(tokenHandler.getCheckedToken("invalid", RefreshToken.class)).thenReturn(null);

    when(tokenHandler.createAccessToken(validAuthCode, "pass")).thenReturn(accessToken);
    when(tokenHandler.createRefreshToken(validAuthCodeWithOfflineAccess, "pass")).thenReturn(refreshToken);
    when(tokenHandler.createAccessToken(refreshToken, refreshToken.getScopeIds(), "pass")).thenReturn(accessTokenWithOfflineAccess);
    when(tokenHandler.createAccessToken(refreshToken, refreshedAccessToken.getScopeIds(), "pass"))
        .thenReturn(refreshedAccessToken);

    when(tokenRepository.getToken(sidToken.getId())).thenReturn(sidToken);

    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);
    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(TokenEndpoint.class);
  }

  @Test public void testUnsupportedGrantType(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("invalid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testAuthCodeMismatchingRedirectUri(JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("valid", "http://whatever.example.net");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromString(resp.readEntity(String.class), TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
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
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testValidAuthCode(JsonFactory jsonFactory, AuthModule.Settings settings, Clock clock) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.getRefreshToken()).isNullOrEmpty();

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo(account.getId());
    assertThat(payload.getAudience()).isEqualTo(appInstance.getId());
    assertThat(payload.getIssuedAtTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()));
    assertThat(payload.getExpirationTimeSeconds()).isEqualTo(payload.getIssuedAtTimeSeconds() + settings.idTokenDuration.getStandardSeconds());
    assertThat(payload.getNonce()).isEqualTo("nonce");
    assertThat(payload.getAuthorizationTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    if (payload.containsKey("app_user")) {
      assertThat(payload.get("app_user")).isEqualTo(Boolean.FALSE);
    }
    if (payload.containsKey("app_admin")) {
      assertThat(payload.get("app_admin")).isEqualTo(Boolean.FALSE);
    }
  }

  @Test public void testValidAuthCodeWithAppUser(JsonFactory jsonFactory, AuthModule.Settings settings, Clock clock,
      AccessControlRepository accessControlRepository) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));
    when(accessControlRepository.getAccessControlEntry(appInstance.getId(), account.getId())).thenReturn(
        new AccessControlEntry() {{
          setId("ace");
          setUser_id(account.getId());
          setInstance_id(appInstance.getId());
        }});

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.getRefreshToken()).isNullOrEmpty();

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo(account.getId());
    assertThat(payload.getAudience()).isEqualTo(appInstance.getId());
    assertThat(payload.getIssuedAtTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()));
    assertThat(payload.getExpirationTimeSeconds()).isEqualTo(payload.getIssuedAtTimeSeconds() + settings.idTokenDuration.getStandardSeconds());
    assertThat(payload.getNonce()).isEqualTo("nonce");
    assertThat(payload.getAuthorizationTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    assertThat(payload.get("app_user")).isEqualTo(Boolean.TRUE);
    if (payload.containsKey("app_admin")) {
      assertThat(payload.get("app_admin")).isEqualTo(Boolean.FALSE);
    }
  }

  @Test public void testValidAuthCodeWithAppAdmin(JsonFactory jsonFactory, AuthModule.Settings settings, Clock clock,
      AppAdminHelper appAdminHelper) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));
    when(appAdminHelper.isAdmin(account.getId(), appInstance)).thenReturn(true);

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.getRefreshToken()).isNullOrEmpty();

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo(account.getId());
    assertThat(payload.getAudience()).isEqualTo(appInstance.getId());
    assertThat(payload.getIssuedAtTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis()));
    assertThat(payload.getExpirationTimeSeconds()).isEqualTo(payload.getIssuedAtTimeSeconds() + settings.idTokenDuration.getStandardSeconds());
    assertThat(payload.getNonce()).isEqualTo("nonce");
    assertThat(payload.getAuthorizationTimeSeconds()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    if (payload.containsKey("app_user")) {
      assertThat(payload.get("app_user")).isEqualTo(Boolean.FALSE);
    }
    assertThat(payload.get("app_admin")).isEqualTo(Boolean.TRUE);
  }

  @Test public void testValidAuthCodeWithOfflineAccess(JsonFactory jsonFactory, AuthModule.Settings settings, Clock clock) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("offline", validAuthCodeWithOfflineAccess.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IdTokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, IdTokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly(Scopes.OFFLINE_ACCESS, "dp1s1", "dp1s3", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), accessTokenWithOfflineAccess.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(accessTokenWithOfflineAccess, "pass"));
    assertThat(response.getRefreshToken()).isEqualTo(TokenSerializer.serialize(refreshToken, "pass"));

    IdToken idToken = response.parseIdToken();
    assertThat(idToken.verifySignature(settings.keyPair.getPublic())).isTrue();

    IdToken.Payload payload = idToken.getPayload();
    assertThat(payload.getIssuer()).isEqualTo(InProcessResteasy.BASE_URI.toString());
    assertThat(payload.getSubject()).isEqualTo(account.getId());
    assertThat(payload.getAudience()).isEqualTo(appInstance.getId());
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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

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
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = refreshToken("valid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, TokenResponse.class);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp3s1");
    assertThat(response.getExpiresInSeconds())
        .isEqualTo(new Duration(new Instant(clock.currentTimeMillis()), refreshedAccessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.getAccessToken()).isEqualTo(TokenSerializer.serialize(refreshedAccessToken, "pass"));
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
