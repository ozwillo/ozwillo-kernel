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
package oasis.web.providers;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.web.utils.ResponseFactory;

@RunWith(JukitoRunner.class)
public class JacksonJsonProviderTest {

  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testRoundtrip() {
    DummyObject able = new DummyObject();
    able.instant = Instant.now();
    able.doubles = ImmutableList.of(Math.random(), Math.random());
    able.optionalDouble = OptionalDouble.of(Math.random());

    List<DummyObject> baker = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .post(Entity.json(able), new GenericType<List<DummyObject>>() {});

    assertThat(Iterables.getOnlyElement(baker)).isEqualToComparingFieldByField(able);
  }

  @Test public void testJsonSyntaxError() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .post(Entity.json("{\"foo\": "));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("Unexpected end-of-input");
  }

  @Test public void testTypeMappingError() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .post(Entity.json("{\"instant\": [] }"));

    assertThat(response.getStatus()).isEqualTo(ResponseFactory.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.readEntity(String.class)).contains("Cannot deserialize instance of `" + Instant.class.getCanonicalName() + "`");
  }

  @Path("/")
  public static class DummyResource {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<DummyObject> echo(DummyObject obj) {
      return Stream.of(obj);
    }
  }

  public static class DummyObject {
    public Instant instant;
    public ImmutableList<Double> doubles;
    public OptionalDouble optionalDouble;
  }
}
