package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;

import javax.ws.rs.core.Response;
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
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;

@RunWith(JukitoRunner.class)
public class LogoutPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LogoutPage.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);

    }
  }

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("sid", SidToken.class)).thenReturn(sidToken);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LogoutPage.class);
  }

  @Test public void testLegacyGet_loggedIn_noContinueUrl(TokenRepository tokenRepository) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class)).request()
        .cookie(UserFilter.COOKIE_NAME, "sid")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(LoginPage.defaultContinueUrl(UriBuilder.fromUri(InProcessResteasy.BASE_URI)));
    assertThat(response.getCookies()).containsKey(UserFilter.COOKIE_NAME);
    assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();

    verify(tokenRepository).revokeToken(sidToken.getId());
  }

  @Test public void testLegacyGet_loggedIn_withContinueUrl(TokenRepository tokenRepository) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam(LoginPage.CONTINUE_PARAM, "http://www.google.com")
        .request()
        .cookie(UserFilter.COOKIE_NAME, "sid")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create("http://www.google.com"));
    assertThat(response.getCookies()).containsKey(UserFilter.COOKIE_NAME);
    assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();

    verify(tokenRepository).revokeToken(sidToken.getId());
  }

  @Test public void testLegacyGet_notLoggedIn_noContinueUrl() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(LoginPage.defaultContinueUrl(UriBuilder.fromUri(InProcessResteasy.BASE_URI)));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();
    }
  }

  @Test public void testLegacyGet_notLoggedIn_withContinueUrl() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam(LoginPage.CONTINUE_PARAM, "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create("http://www.google.com"));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();
    }
  }
}
