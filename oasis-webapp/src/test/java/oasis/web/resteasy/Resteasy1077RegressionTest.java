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
package oasis.web.resteasy;

import static org.assertj.core.api.Assertions.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;

/**
 * Make we don't run in RESTEASY-1077 when upgrading Resteasy.
 */
@RunWith(JukitoRunner.class)
public class Resteasy1077RegressionTest {
  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(Resource.class);
  }

  @Test
  public void testTrailingSlash() throws Exception {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path("/test/"))
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo("test");
  }

  @Test
  public void testNoTrailingSlash() throws Exception {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path("/test"))
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.MOVED_PERMANENTLY);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path("/test/").build());
  }

  @Path("/")
  public static class Resource {
    @Path("/test/")
    @GET
    public Response test(@Context UriInfo uriInfo) {
      // XXX: can't use getPath() because of https://issues.jboss.org/browse/RESTEASY-1124
      if (uriInfo.getRequestUri().getPath().endsWith("/")) {
        return Response.ok("test", MediaType.TEXT_PLAIN_TYPE).build();
      } else {
        return Response
            .status(Response.Status.MOVED_PERMANENTLY)
            .location(uriInfo.getBaseUriBuilder().path("/test/").build())
            .build();
      }
    }
  }
}
