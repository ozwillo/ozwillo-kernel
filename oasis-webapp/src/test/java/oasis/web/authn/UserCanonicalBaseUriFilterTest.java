package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

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
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.TokenHandler;
import oasis.web.utils.UserAgentFingerprinter;

@RunWith(JukitoRunner.class)
public class UserCanonicalBaseUriFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserCanonicalBaseUriFilter.class);

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder()
          .setCanonicalBaseUri(URI.create("http://example.com/somepath/"))
          .build());
    }
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserCanonicalBaseUriFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testRedirectToCanonicalUri() {
    Response response = resteasy.getClient().target("/foo/bar/baz?qux=quux").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.MOVED_PERMANENTLY);
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
