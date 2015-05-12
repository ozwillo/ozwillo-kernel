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

import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
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

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.web.utils.UserAgentFingerprinter;

@RunWith(JukitoRunner.class)
public class UserFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserFilter.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(UserAgentFingerprinter.class).in(TestSingleton.class);
    }
  }

  static final Instant now = Instant.now();

  static final SidToken validSidToken = new SidToken();
  static {
    validSidToken.setId("validSession");
    validSidToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validSidToken.setExpirationTime(now.plus(Duration.standardHours(1)));
    validSidToken.setUserAgentFingerprint("fingerprint".getBytes(StandardCharsets.UTF_8));
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Inject UserAgentFingerprinter fingerprinter;
  @Inject TokenRepository tokenRepository;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", SidToken.class)).thenReturn(validSidToken);
    when(tokenHandler.getCheckedToken("invalid", SidToken.class)).thenReturn(null);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testNoCookie(TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request().get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).doesNotContainKey(UserFilter.COOKIE_NAME);
    assertThat(response.readEntity(SidToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler, tokenRepository);
  }

  @Test public void testAuthenticated() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());

    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request()
        .cookie(UserFilter.COOKIE_NAME, "valid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKey(UserFilter.COOKIE_NAME);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewToken(validSidToken.getId());
  }

  @Test public void testWithInvalidCookie() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request()
        .cookie(UserFilter.COOKIE_NAME, "invalid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).containsKey(UserFilter.COOKIE_NAME);
    assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();
    assertThat(response.readEntity(SidToken.class)).isNull();

    verify(tokenRepository, never()).renewToken(validSidToken.getId());
  }

  @Test public void testWithInvalidFingerprint() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn("attacker".getBytes(StandardCharsets.UTF_8));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request()
        .cookie(UserFilter.COOKIE_NAME, "valid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).containsKey(UserFilter.COOKIE_NAME);
    assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();
    assertThat(response.readEntity(SidToken.class)).isNull();

    verify(tokenRepository, never()).renewToken(validSidToken.getId());
  }

  private void commonAssertions(Response response) {
    assertThat(response.getHeaders()).containsKeys(HttpHeaders.VARY, HttpHeaders.CACHE_CONTROL);
    assertThat(response.getHeaderString(HttpHeaders.VARY).split("\\s*,\\s*")).contains(HttpHeaders.COOKIE);
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL).split("\\s*,\\s*")).contains("private");
  }

  @Path("/")
  @User
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SidToken get() {
      final UserSessionPrincipal principal = (UserSessionPrincipal) securityContext.getUserPrincipal();
      return principal == null ? null : principal.getSidToken();
    }
  }
}
