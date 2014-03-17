package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.SidToken;
import oasis.services.authn.TokenHandler;

@RunWith(JukitoRunner.class)
public class UserFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserFilter.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
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

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", SidToken.class)).thenReturn(validSidToken);
    when(tokenHandler.getCheckedToken("invalid", SidToken.class)).thenReturn(null);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testNoCookie() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request().get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.readEntity(SidToken.class)).isNull();
  }

  @Test public void testAuthenticated() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request()
        .cookie(UserFilter.COOKIE_NAME, "valid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);
  }

  @Test public void testWithInvalidCookie() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).build()).request()
        .cookie(UserFilter.COOKIE_NAME, "invalid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.readEntity(SidToken.class)).isNull();
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
