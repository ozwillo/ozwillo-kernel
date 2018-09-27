/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.urls.ImmutableBaseUrls;
import oasis.urls.ImmutablePathUrls;
import oasis.urls.UrlsModule;

@RunWith(JukitoRunner.class)
public class UserCanonicalBaseUriFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserCanonicalBaseUriFilter.class);

      install(new UrlsModule(ImmutableBaseUrls.builder()
          .canonicalBaseUri(URI.create("http://example.com/somepath/"))
          .build(), ImmutablePathUrls.builder().build()));
    }
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserCanonicalBaseUriFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testRedirectToCanonicalUri() {
    Response response = resteasy.getClient().target("/foo/bar/baz?qux=quux").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create("http://example.com/somepath/foo/bar/baz?qux=quux"));
  }

  @Path("/foo/{bar}/baz")
  @User
  public static class DummyResource {
    @PathParam("bar")
    private String bar;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
      return bar;
    }
  }
}
