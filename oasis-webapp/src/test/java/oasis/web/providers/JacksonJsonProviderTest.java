package oasis.web.providers;

import static org.assertj.core.api.Assertions.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.Instant;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
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

    DummyObject baker = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).build())
        .request()
        .post(Entity.json(able), DummyObject.class);

    assertThat(baker).isEqualToComparingFieldByField(able);
  }

  @Test public void testJsonSyntaxError() {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).build())
        .request()
        .post(Entity.json("{\"foo\": "));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("Unexpected end-of-input");
  }

  @Test public void testTypeMappingError() {
    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class).build())
        .request()
        .post(Entity.json("{\"instant\": [] }"));

    assertThat(response.getStatus()).isEqualTo(ResponseFactory.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.readEntity(String.class)).contains("Can not deserialize instance of " + Instant.class.getName());
  }

  @Path("/")
  public static class DummyResource {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DummyObject echo(DummyObject obj) {
      return obj;
    }
  }

  public static class DummyObject {
    public Instant instant;
    public ImmutableList<Double> doubles;
  }
}
