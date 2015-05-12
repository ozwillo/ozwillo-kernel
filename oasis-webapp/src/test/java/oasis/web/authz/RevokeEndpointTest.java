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

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.AccessToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class RevokeEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(RevokeEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);

      bind(JsonFactory.class).to(JacksonFactory.class);
    }
  }

  static final AccessToken validToken = new AccessToken() {{
    setId("valid");
    setServiceProviderId("sp");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken(eq("valid"), any(Class.class))).thenReturn(validToken);
    when(tokenHandler.getCheckedToken(eq("invalid"), any(Class.class))).thenReturn(null);
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

  @Test public void testMissingToken(TokenRepository tokenRepository, JsonFactory jsonFactory) throws Throwable {
    // when
    Response resp = revoke(new Form());

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
  }

  @Test public void testMultipleTokens(TokenRepository tokenRepository, JsonFactory jsonFactory) throws Throwable {
    // when
    Response resp = revoke(new Form()
      .param("token", "first")
      .param("token", "second"));

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("invalid_request");
  }

  @Test public void testRevocation(TokenRepository tokenRepository) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("sp"));

    // when
    Response resp = revoke("valid");

    // then
    verify(tokenRepository).revokeToken(validToken.getId());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
  }

  @Test public void testBadClient(TokenRepository tokenRepository, JsonFactory jsonFactory) throws Throwable {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp"));

    // when
    Response resp = revoke("valid");

    // then
    verify(tokenRepository, never()).revokeToken(anyString());
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    TokenErrorResponse response = jsonFactory.fromInputStream(resp.readEntity(InputStream.class), StandardCharsets.UTF_8, TokenErrorResponse.class);
    assertThat(response.getError()).isEqualTo("unauthorized_client");
  }

  private Response revoke(String token) {
    return revoke(new Form("token", token));
  }

  private Response revoke(Form form) {
    return resteasy.getClient().target(UriBuilder.fromResource(RevokeEndpoint.class)).request()
        .post(Entity.form(form));
  }
}
