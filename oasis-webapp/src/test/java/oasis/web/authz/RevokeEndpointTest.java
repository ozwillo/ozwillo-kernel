package oasis.web.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ServiceProvider;
import oasis.model.authn.AccessToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class RevokeEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(RevokeEndpoint.class);

      bind(Clock.class).to(FixedClock.class);
      bind(FixedClock.class).in(TestSingleton.class);

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder().build());
    }
  }

  static final AccessToken validToken = new AccessToken() {{
    setId("valid");
    setServiceProviderId("sp");
    setScopeIds(ImmutableSet.of("openid", "dp1s1", "dp1s3", "dp3s1"));
    expiresIn(Duration.standardDays(1));
  }};
  static final AccessToken expiredToken = new AccessToken() {{
    setId("expired");
    setServiceProviderId("sp");
    setCreationTime(new DateTime(2008, 1, 20, 11, 10).toInstant());
    setExpirationTime(new DateTime(2013, 10, 30, 19, 42).toInstant());
  }};
  static final Token notAnOAuthToken = new Token() {{
    setId("notAnOAuthToken");
    expiresIn(Duration.standardDays(1));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUpMocks(TokenRepository tokenRepository) {
    when(tokenRepository.getToken(validToken.getId())).thenReturn(validToken);
    when(tokenRepository.getToken(expiredToken.getId())).thenReturn(expiredToken);
    when(tokenRepository.getToken(notAnOAuthToken.getId())).thenReturn(notAnOAuthToken);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(RevokeEndpoint.class);
  }

  @Test public void testInvalidToken(TokenRepository tokenRepository) {
    // when
    Response resp = revoke("invalid");

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
  }

  @Test public void testMissingToken(TokenRepository tokenRepository) {
    // when
    Response resp = revoke(new Form());

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = resp.readEntity(TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
  }

  @Test public void testMultipleTokens(TokenRepository tokenRepository) {
    // when
    Response resp = revoke(new Form()
      .param("token", "first")
      .param("token", "second"));

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = resp.readEntity(TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
  }

  @Test public void testExpiredToken(TokenRepository tokenRepository) {
    // when
    Response resp = revoke(expiredToken);

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
  }


  @Test public void testNotAnOAuthToken(TokenRepository tokenRepository) {
    // when
    Response resp = revoke(notAnOAuthToken);

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
  }

  @Test public void testRevocation(TokenRepository tokenRepository) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = revoke(validToken);

    // then
    verify(tokenRepository).revokeToken(validToken.getId());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
  }

  @Test public void testBadClient(TokenRepository tokenRepository) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp"));

    // when
    Response resp = revoke(validToken);

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = resp.readEntity(TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("unauthorized_client");
  }

  private Response revoke(Token token) {
    return revoke(TokenSerializer.serialize(token));
  }

  private Response revoke(String token) {
    return revoke(new Form("token", token));
  }

  private Response revoke(Form form) {
    return resteasy.getClient().target(UriBuilder.fromResource(RevokeEndpoint.class)).request()
        .post(Entity.form(form));
  }
}
