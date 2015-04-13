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
import oasis.auth.AuthModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.Urls;
import oasis.urls.UrlsModule;

@RunWith(JukitoRunner.class)
public class UserCanonicalBaseUriFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserCanonicalBaseUriFilter.class);

      install(new UrlsModule(ImmutableUrls.builder()
          .canonicalBaseUri(URI.create("http://example.com/somepath/"))
          .build()));
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
