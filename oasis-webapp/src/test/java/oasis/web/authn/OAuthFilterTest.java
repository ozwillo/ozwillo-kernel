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
package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.regex.Pattern;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.AccessToken;
import oasis.services.authn.TokenHandler;

@RunWith(JukitoRunner.class)
public class OAuthFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(OAuthFilter.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
    }
  }

  static final String SCOPE_DATA = "http://www.nsa.gov/scope/all-your-base-are-belong-to-us";
  static final String SCOPE_MIND = "http://www.nsa.gov/scope/even-your-mind";
  static final String SCOPE_COOKIES = "http://www.nsa.gov/scope/but-we-have-cookies";

  static final Instant now = Instant.now();

  static final AccessToken validAccessToken = new AccessToken();
  static {
    validAccessToken.setId("valid");
    validAccessToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validAccessToken.setExpirationTime(now.plus(Duration.standardHours(1)));
    validAccessToken.setScopeIds(ImmutableSet.of(SCOPE_DATA, SCOPE_MIND));
  }
  static final AccessToken invalidAccessToken = new AccessToken();
  static {
    invalidAccessToken.setId("invalid");
    invalidAccessToken.setCreationTime(now.minus(Duration.standardHours(1)));
    invalidAccessToken.setExpirationTime(now.plus(Duration.standardHours(1)));
  }
  static final AccessToken accessTokenWithoutScope = new AccessToken();
  static {
    accessTokenWithoutScope.setId("without_scope");
    accessTokenWithoutScope.setCreationTime(now.minus(Duration.standardHours(1)));
    accessTokenWithoutScope.setExpirationTime(now.plus(Duration.standardHours(1)));
    accessTokenWithoutScope.setScopeIds(Collections.<String>emptySet());
  }
  static final AccessToken accessTokenWithInsufficientScopes = new AccessToken();
  static {
    accessTokenWithInsufficientScopes.setId("insufficient_scope");
    accessTokenWithInsufficientScopes.setCreationTime(now.minus(Duration.standardHours(1)));
    accessTokenWithInsufficientScopes.setExpirationTime(now.plus(Duration.standardHours(1)));
    accessTokenWithInsufficientScopes.setScopeIds(ImmutableSet.of(SCOPE_DATA));
  }
  static final AccessToken accessTokenWithTooMuchScopes = new AccessToken();
  static {
    accessTokenWithTooMuchScopes.setId("too_much_scope");
    accessTokenWithTooMuchScopes.setCreationTime(now.minus(Duration.standardHours(1)));
    accessTokenWithTooMuchScopes.setExpirationTime(now.plus(Duration.standardHours(1)));
    accessTokenWithTooMuchScopes.setScopeIds(ImmutableSet.of(SCOPE_DATA, SCOPE_MIND, SCOPE_COOKIES));
  }

  static final Pattern STARTS_WITH_BEARER_PATTERN = Pattern.compile("^Bearer .*$", Pattern.CASE_INSENSITIVE);

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", AccessToken.class)).thenReturn(validAccessToken);
    when(tokenHandler.getCheckedToken("invalid", AccessToken.class)).thenReturn(null);
    when(tokenHandler.getCheckedToken("without_scope", AccessToken.class)).thenReturn(accessTokenWithoutScope);
    when(tokenHandler.getCheckedToken("insufficient_scope", AccessToken.class)).thenReturn(accessTokenWithInsufficientScopes);
    when(tokenHandler.getCheckedToken("too_much_scope", AccessToken.class)).thenReturn(accessTokenWithTooMuchScopes);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(OAuthFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test
  public void testWithoutAuthorizationHeader(TokenHandler tokenHandler) {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
    assertThat(response.readEntity(AccessToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler);
  }

  @Test
  public void testWithUnknownAuthorizationScheme(TokenHandler tokenHandler) {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Unknown scheme")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
    assertThat(response.readEntity(AccessToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler);
  }

  @Test
  public void testWithInvalidAuthorizationHeader() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer wow such invalid very bug")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE))
        .matches(STARTS_WITH_BEARER_PATTERN)
        .contains("error=\"invalid_request\"");
  }

  @Test
  public void testWithAuthorizationHeaderWithoutBearerCode() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE))
        .matches(STARTS_WITH_BEARER_PATTERN)
        .contains("error=\"invalid_request\"");
  }

  @Test
  public void testWithUnknownAccessToken() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE))
        .matches(STARTS_WITH_BEARER_PATTERN)
        .contains("error=\"invalid_token\"");
  }

  @Test
  public void testWithAccessTokenWithoutScope() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer without_scope")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.FORBIDDEN);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE))
        .matches(STARTS_WITH_BEARER_PATTERN)
        .contains("error=\"insufficient_scope\"");
  }

  @Test
  public void testWithAccessTokenWithInsufficientScopes() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer insufficient_scope")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.FORBIDDEN);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE))
        .matches(STARTS_WITH_BEARER_PATTERN)
        .contains("error=\"insufficient_scope\"");
  }

  @Test
  public void testWithAccessTokenWithTooMuchScopes() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer too_much_scope")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(AccessToken.class)).isEqualToComparingFieldByField(accessTokenWithTooMuchScopes);
  }

  @Test
  public void testWithValidAccessToken() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(AccessToken.class)).isEqualToComparingFieldByField(validAccessToken);
  }

  @Path("/")
  @OAuth @WithScopes({SCOPE_DATA, SCOPE_MIND})
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AccessToken get() {
      final OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      return principal == null ? null : principal.getAccessToken();
    }
  }
}
