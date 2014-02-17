package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;

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
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;

@RunWith(JukitoRunner.class)
public class UserAuthenticationFilterTest {

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserAuthenticationFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testNoCookie() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).queryParam("qux", "quux").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(Login.class).build());
    assertThat(location.getPath()).isEqualTo("/a/login");
    assertThat(location.getQueryParameters().getFirst("continue")).isEqualTo("http://localhost/foo/bar?qux=quux");
  }

  @Test public void testAuthenticated() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).queryParam("qux", "quux").request()
        .cookie(UserAuthenticationFilter.COOKIE_NAME, "account")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo("account");
  }

  @Path("/foo/bar")
  @Authenticated @User
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
      return ((AccountPrincipal) securityContext.getUserPrincipal()).getAccountId();
    }
  }
}
