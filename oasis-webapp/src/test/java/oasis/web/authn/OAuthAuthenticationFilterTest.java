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
import oasis.web.authn.testing.TestOAuthFilter;

@RunWith(JukitoRunner.class)
public class OAuthAuthenticationFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(OAuthAuthenticationFilter.class);
    }
  }

  static final Instant now = Instant.now();

  static final AccessToken validAccessToken = new AccessToken();
  static {
    validAccessToken.setId("valid");
    validAccessToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validAccessToken.setExpirationTime(now.plus(Duration.standardHours(1)));
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(OAuthAuthenticationFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test
  public void testUnauthenticated() {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "authRequired").build())
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE)).isEqualToIgnoringCase("Bearer");
  }

  @Test
  public void testAuthenticated() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(validAccessToken));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "authRequired").build())
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(AccessToken.class)).isEqualToComparingFieldByField(validAccessToken);
  }

  @Test
  public void testChallengeResponse() {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "challenge").build())
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE)).isEqualToIgnoringCase("Bearer");
  }

  @Path("/")
  @OAuth
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Path("/foo/bar")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public AccessToken authRequired() {
      return ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    }

    @GET
    @Path("baz/qux")
    public Response challenge() {
      assertThat(securityContext.getUserPrincipal()).isNull();
      return OAuthAuthenticationFilter.challengeResponse();
    }
  }
}
