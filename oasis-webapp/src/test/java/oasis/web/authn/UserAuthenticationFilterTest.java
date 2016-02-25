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

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyUriInfo;
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
import oasis.services.authn.TokenHandler;
import oasis.web.authn.testing.TestUserFilter;

@RunWith(JukitoRunner.class)
public class UserAuthenticationFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserAuthenticationFilter.class);
    }
  }

  static final Instant now = Instant.now();

  static final SidToken validSidToken = new SidToken();
  static {
    validSidToken.setId("validSession");
    validSidToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validSidToken.setExpirationTime(now.plus(Duration.standardHours(1)));
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserAuthenticationFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testUnauthenticated() {
    URI requestUri = resteasy.getBaseUriBuilder()
        .path(DummyResource.class).path(DummyResource.class, "authRequired")
        .queryParam("baz", "baz").queryParam("qux", "qu&ux")
        .build();
    Response response = resteasy.getClient().target(requestUri).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(resteasy.getBaseUriBuilder().path(LoginPage.class).build());
    assertThat(location.getQueryParameters().getFirst("continue")).isEqualTo(requestUri.toString());
  }

  @Test public void testAuthenticated() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(validSidToken));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).path(DummyResource.class, "authRequired").build())
        .queryParam("qux", "quux")
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);
  }

  @Test public void testLoginResponse() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(validSidToken));

    URI requestUri = resteasy.getBaseUriBuilder()
        .path(DummyResource.class).path(DummyResource.class, "redirectToLogin")
        .queryParam("foo", "b&ar").queryParam("baz", "baz")
        .build();
    Response response = resteasy.getClient().target(requestUri).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(resteasy.getBaseUriBuilder().path(LoginPage.class).build());
    assertThat(location.getQueryParameters().getFirst("continue")).isEqualTo(requestUri.toString());

    // Make sure we don't log the user out!
    assertThat(response.getCookies()).doesNotContainKey(UserFilter.COOKIE_NAME);
  }

  @Path("/")
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @Path("/foo/bar")
    @GET
    @Authenticated @User
    @Produces(MediaType.APPLICATION_JSON)
    public SidToken authRequired() {
      return ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    }

    @Path("/qux/quux")
    @GET
    @User
    public Response redirectToLogin(@Context UriInfo uriInfo) {
      assertThat(securityContext.getUserPrincipal()).isNotNull();

      return UserAuthenticationFilter.loginResponse(uriInfo.getRequestUri(), null, null);
    }
  }
}
