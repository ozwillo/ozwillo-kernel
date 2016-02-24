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
import static org.jose4j.jwa.AlgorithmConstraints.ConstraintType.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

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
import oasis.model.authn.JtiRepository;
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
import oasis.web.authz.TokenEndpoint.TokenResponse;
import oasis.web.openidconnect.ErrorResponse;

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
  }

  static final Instant now = new DateTime(2014, 7, 17, 14, 30).toInstant();
  static final Instant tomorrow = now.plus(Duration.standardDays(1));
  static final Instant oneHourAgo = now.minus(Duration.standardHours(1));

  static final String validCodeVerifier = "let-this-test-string-be-a-valid-code-verifier";
  static final String validCodeChallenge = "X4qcuPsv3LQP2ZS7toD1-TWQUUMtscLgKhAUhVqatJw";

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
  static final AuthorizationCode validAuthCodeWithCodeChallenge = new AuthorizationCode() {{
    setId("validAuthCode");
    setAccountId(account.getId());
    setCreationTime(now.minus(Duration.standardHours(1)));
    expiresIn(Duration.standardHours(2));
    setParent(sidToken);
    setServiceProviderId(appInstance.getId());
    setScopeIds(ImmutableSet.of("dp1s1", "dp1s3", "dp3s1"));
    setRedirectUri("http://sp.example.com/callback");
    setNonce("nonce");
    setCodeChallenge(validCodeChallenge);
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
  static final AccessToken accessTokenFromJwtBearer = new AccessToken() {{
    setId("accessTokenFromJwtBearer");
    setAccountId(TokenEndpoint.ANONYMOUS_ACCOUNT_ID);
    setCreationTime(now);
    expiresIn(Duration.standardDays(1));
    setAncestorIds(ImmutableList.of("jti"));
    setServiceProviderId(appInstance.getId());
    setScopeIds(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES);
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler, TokenRepository tokenRepository, AccountRepository accountRepository,
      AppInstanceRepository appInstanceRepository, JtiRepository jtiRepository) {
    when(tokenHandler.generateRandom()).thenReturn("pass");

    when(tokenHandler.getCheckedToken("valid", AuthorizationCode.class)).thenReturn(validAuthCode);
    when(tokenHandler.getCheckedToken("offline", AuthorizationCode.class)).thenReturn(validAuthCodeWithOfflineAccess);
    when(tokenHandler.getCheckedToken("pkce", AuthorizationCode.class)).thenReturn(validAuthCodeWithCodeChallenge);
    when(tokenHandler.getCheckedToken("invalid", AuthorizationCode.class)).thenReturn(null);
    when(tokenHandler.getCheckedToken("valid", RefreshToken.class)).thenReturn(refreshToken);
    when(tokenHandler.getCheckedToken("invalid", RefreshToken.class)).thenReturn(null);

    when(tokenHandler.createAccessToken(validAuthCode, "pass")).thenReturn(accessToken);
    when(tokenHandler.createAccessToken(validAuthCodeWithCodeChallenge, "pass")).thenReturn(accessToken);
    when(tokenHandler.createRefreshToken(validAuthCodeWithOfflineAccess, "pass")).thenReturn(refreshToken);
    when(tokenHandler.createAccessToken(refreshToken, refreshToken.getScopeIds(), "pass")).thenReturn(accessTokenWithOfflineAccess);
    when(tokenHandler.createAccessToken(refreshToken, refreshedAccessToken.getScopeIds(), "pass"))
        .thenReturn(refreshedAccessToken);
    when(tokenHandler.createAccessTokenForJWTBearer("jti", TokenEndpoint.ANONYMOUS_ACCOUNT_ID, TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES, appInstance.getId(), "pass"))
        .thenReturn(accessTokenFromJwtBearer);

    when(tokenRepository.getToken(sidToken.getId())).thenReturn(sidToken);

    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);
    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);

    when(jtiRepository.markAsUsed(eq("jti"), any(Instant.class))).thenReturn(true);
    when(jtiRepository.markAsUsed(eq("reused"), any(Instant.class))).thenReturn(false);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(TokenEndpoint.class);
  }

  @Test public void testUnsupportedGrantType() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form("grant_type", "unsupported")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("unsupported_grant_type");
  }

  @Test public void testMissingGrantType() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form("code", "valid")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("grant_type");
  }

  @Test public void testDuplicateGrantType() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("grant_type", "authorization_code")
            .param("code", "valid")));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("grant_type");
  }

  @Test public void testInvalidAuthCode() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("invalid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testAuthCodeMismatchingRedirectUri() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("valid", "http://whatever.example.net");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
    assertThat(response.getError_description()).contains("redirect_uri");
  }

  @Test public void testStolenAuthCode() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp2"));

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testValidAuthCode(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.expires_in).isEqualTo(new Duration(now, accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();
    assertThat(response.id_token).isNotEmpty();

    JwtClaims claims = new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
        .setVerificationKey(settings.keyPair.getPublic())
        .setExpectedIssuer(resteasy.getBaseUri().toString())
        .setExpectedSubject(account.getId())
        .setExpectedAudience(appInstance.getId())
        .setRequireIssuedAt()
        .setRequireExpirationTime()
        .setEvaluationTime(NumericDate.fromMilliseconds(now.getMillis()))
        .build()
        .processToClaims(response.id_token);

    assertThat(claims.getIssuedAt().getValue()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(now.getMillis()));
    assertThat(claims.getExpirationTime().getValue()).isEqualTo(claims.getIssuedAt().getValue() + settings.idTokenDuration.getStandardSeconds());
    assertThat(claims.getStringClaimValue("nonce")).isEqualTo("nonce");
    assertThat(claims.getNumericDateClaimValue("auth_time").getValue()).isEqualTo(
        TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    if (claims.hasClaim("app_user")) {
      assertThat(claims.getClaimValue("app_user")).isEqualTo(Boolean.FALSE);
    }
    if (claims.hasClaim("app_admin")) {
      assertThat(claims.getClaimValue("app_admin")).isEqualTo(Boolean.FALSE);
    }
  }

  @Test public void testValidAuthCodeWithAppUser(AuthModule.Settings settings, AccessControlRepository accessControlRepository) throws Throwable {
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
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.expires_in).isEqualTo(new Duration(now, accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();

    JwtClaims claims = new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
        .setVerificationKey(settings.keyPair.getPublic())
        .setExpectedIssuer(resteasy.getBaseUri().toString())
        .setExpectedSubject(account.getId())
        .setExpectedAudience(appInstance.getId())
        .setRequireIssuedAt()
        .setRequireExpirationTime()
        .setEvaluationTime(NumericDate.fromMilliseconds(now.getMillis()))
        .build()
        .processToClaims(response.id_token);

    assertThat(claims.getIssuedAt().getValue()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(now.getMillis()));
    assertThat(claims.getExpirationTime().getValue()).isEqualTo(claims.getIssuedAt().getValue() + settings.idTokenDuration.getStandardSeconds());
    assertThat(claims.getStringClaimValue("nonce")).isEqualTo("nonce");
    assertThat(claims.getNumericDateClaimValue("auth_time").getValue()).isEqualTo(
        TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    assertThat(claims.getClaimValue("app_user")).isEqualTo(Boolean.TRUE);
    if (claims.hasClaim("app_admin")) {
      assertThat(claims.getClaimValue("app_admin")).isEqualTo(Boolean.FALSE);
    }

  }

  @Test public void testValidAuthCodeWithAppAdmin(AuthModule.Settings settings,
      AppAdminHelper appAdminHelper) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));
    when(appAdminHelper.isAdmin(account.getId(), appInstance)).thenReturn(true);

    // when
    Response resp = authCode("valid", validAuthCode.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.expires_in).isEqualTo(new Duration(now, accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();

    JwtClaims claims = new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
        .setVerificationKey(settings.keyPair.getPublic())
        .setExpectedIssuer(resteasy.getBaseUri().toString())
        .setExpectedSubject(account.getId())
        .setExpectedAudience(appInstance.getId())
        .setRequireIssuedAt()
        .setRequireExpirationTime()
        .setEvaluationTime(NumericDate.fromMilliseconds(now.getMillis()))
        .build()
        .processToClaims(response.id_token);

    assertThat(claims.getIssuedAt().getValue()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(now.getMillis()));
    assertThat(claims.getExpirationTime().getValue()).isEqualTo(claims.getIssuedAt().getValue() + settings.idTokenDuration.getStandardSeconds());
    assertThat(claims.getStringClaimValue("nonce")).isEqualTo("nonce");
    assertThat(claims.getNumericDateClaimValue("auth_time").getValue()).isEqualTo(
        TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    if (claims.hasClaim("app_user")) {
      assertThat(claims.getClaimValue("app_user")).isEqualTo(Boolean.FALSE);
    }
    assertThat(claims.getClaimValue("app_admin")).isEqualTo(Boolean.TRUE);
  }

  @Test public void testValidAuthCodeWithOfflineAccess(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCode("offline", validAuthCodeWithOfflineAccess.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly(Scopes.OFFLINE_ACCESS, "dp1s1", "dp1s3", "dp3s1");
    assertThat(response.expires_in).isEqualTo(
        new Duration(now, accessTokenWithOfflineAccess.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessTokenWithOfflineAccess, "pass"));
    assertThat(response.refresh_token).isEqualTo(TokenSerializer.serialize(refreshToken, "pass"));

    JwtClaims claims = new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
        .setVerificationKey(settings.keyPair.getPublic())
        .setExpectedIssuer(resteasy.getBaseUri().toString())
        .setExpectedSubject(account.getId())
        .setExpectedAudience(appInstance.getId())
        .setRequireIssuedAt()
        .setRequireExpirationTime()
        .setEvaluationTime(NumericDate.fromMilliseconds(now.getMillis()))
        .build()
        .processToClaims(response.id_token);

    assertThat(claims.getIssuedAt().getValue()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(now.getMillis()));
    assertThat(claims.getExpirationTime().getValue()).isEqualTo(claims.getIssuedAt().getValue() + settings.idTokenDuration.getStandardSeconds());
    assertThat(claims.getStringClaimValue("nonce")).isEqualTo("nonce");
  }

  private Response authCode(String authorizationCode, String redirectUri) {
    return resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("code", authorizationCode)
            .param("redirect_uri", redirectUri)));
  }

  @Test public void testValidAuthCodeWithCodeChallenge(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = authCodeWithCodeVerifier("pkce", validAuthCodeWithCodeChallenge.getRedirectUri(), validCodeVerifier);

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly("dp1s1", "dp1s3", "dp3s1");
    assertThat(response.expires_in).isEqualTo(
        new Duration(now, accessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessToken, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();

    JwtClaims claims = new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
        .setVerificationKey(settings.keyPair.getPublic())
        .setExpectedIssuer(resteasy.getBaseUri().toString())
        .setExpectedSubject(account.getId())
        .setExpectedAudience(appInstance.getId())
        .setRequireIssuedAt()
        .setRequireExpirationTime()
        .setEvaluationTime(NumericDate.fromMilliseconds(now.getMillis()))
        .build()
        .processToClaims(response.id_token);

    assertThat(claims.getIssuedAt().getValue()).isEqualTo(TimeUnit.MILLISECONDS.toSeconds(now.getMillis()));
    assertThat(claims.getExpirationTime().getValue()).isEqualTo(claims.getIssuedAt().getValue() + settings.idTokenDuration.getStandardSeconds());
    assertThat(claims.getStringClaimValue("nonce")).isEqualTo("nonce");
    assertThat(claims.getNumericDateClaimValue("auth_time").getValue()).isEqualTo(
        TimeUnit.MILLISECONDS.toSeconds(sidToken.getAuthenticationTime().getMillis()));
    if (claims.hasClaim("app_user")) {
      assertThat(claims.getClaimValue("app_user")).isEqualTo(Boolean.FALSE);
    }
    if (claims.hasClaim("app_admin")) {
      assertThat(claims.getClaimValue("app_admin")).isEqualTo(Boolean.FALSE);
    }
  }

  @Test public void testAuthCodeMissingCodeVerifier() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCode("pkce", validAuthCodeWithCodeChallenge.getRedirectUri());

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  @Test public void testAuthCodeCodeVerifierTooShort() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCodeWithCodeVerifier("pkce", validAuthCodeWithCodeChallenge.getRedirectUri(), "too-short");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  @Test public void testAuthCodeCodeVerifierTooLong() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCodeWithCodeVerifier("pkce", validAuthCodeWithCodeChallenge.getRedirectUri(),
        Strings.repeat(validCodeVerifier, 4));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  @Test public void testAuthCodeInvalidCodeVerifier() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCodeWithCodeVerifier("pkce", validAuthCodeWithCodeChallenge.getRedirectUri(),
        validCodeVerifier.replace('-', '/'));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  @Test public void testAuthCodeMismatchingCodeVerifier() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCodeWithCodeVerifier("pkce", validAuthCodeWithCodeChallenge.getRedirectUri(),
        validCodeVerifier.replace('-', '.'));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  @Test public void testAuthCodeUnwantedCodeVerifier() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = authCodeWithCodeVerifier("valid", validAuthCode.getRedirectUri(), validCodeVerifier);

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
    assertThat(response.getError_description()).contains("code_verifier");
  }

  private Response authCodeWithCodeVerifier(String authorizationCode, String redirectUri, String codeVerifier) {
    return resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("code", authorizationCode)
            .param("redirect_uri", redirectUri)
            .param("code_verifier", codeVerifier)));
  }

  @Test public void testInvalidRefreshToken() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = refreshToken("invalid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testRefreshTokenMismatchingScopes() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = refreshToken("valid", "dp1s1 unknown dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_scope");
  }

  @Test public void testStolenRefreshToken() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp2"));

    // when
    Response resp = refreshToken("valid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_token");
  }

  @Test public void testValidRefreshToken() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    Response resp = refreshToken("valid", "dp1s1 dp3s1");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly("dp1s1", "dp3s1");
    assertThat(response.expires_in).isEqualTo(new Duration(tomorrow, refreshedAccessToken.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(refreshedAccessToken, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();
  }

  private Response refreshToken(String refreshToken, String scope) {
    return resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "refresh_token")
            .param("refresh_token", refreshToken)
            .param("scope", scope)));
  }

  @Test public void testJwtBearerWithReusedJti(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("reused");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithBadSignature() throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(KeyPairLoader.generateRandomKeyPair().getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithBadIssuer(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer("http://example.com");
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithBadAudience(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience("http://example.com");
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithBadSubject(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject("foo");
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithNoExpirationTime(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testJwtBearerWithPassedExpirationTime(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromMilliseconds(oneHourAgo.minus(Duration.standardDays(1)).getMillis()));
    claims.setExpirationTime(NumericDate.fromMilliseconds(oneHourAgo.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    ErrorResponse response = resp.readEntity(ErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_grant");
  }

  @Test public void testValidJwtBearer(AuthModule.Settings settings) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(appInstance.getId()));

    // when
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setAudience(UriBuilder.fromUri(resteasy.getBaseUri()).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    // Note: no "issued at"
    claims.setExpirationTime(NumericDate.fromMilliseconds(tomorrow.getMillis()));
    claims.setJwtId("jti");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    Response resp = jwtBearer(jws.getCompactSerialization(), Joiner.on(" ").join(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES));

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    TokenResponse response = resp.readEntity(TokenResponse.class);
    assertThat(response.token_type).isEqualTo("Bearer");
    assertThat(response.scope.split(" ")).containsOnly(Iterables.toArray(TokenEndpoint.AUTHORIZED_JWT_BEARER_SCOPES, String.class));
    assertThat(response.expires_in).isEqualTo(new Duration(now, accessTokenFromJwtBearer.getExpirationTime()).getStandardSeconds());
    assertThat(response.access_token).isEqualTo(TokenSerializer.serialize(accessTokenFromJwtBearer, "pass"));
    assertThat(response.refresh_token).isNullOrEmpty();
    assertThat(response.id_token).isNullOrEmpty();
  }

  private Response jwtBearer(String jwt, String scope) throws Exception {
    return resteasy.getClient().target(resteasy.getBaseUriBuilder().path(TokenEndpoint.class).build()).request()
        .post(Entity.form(new Form()
            .param("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .param("assertion", jwt)
            .param("scope", scope)));
  }
}
