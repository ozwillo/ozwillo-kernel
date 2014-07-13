package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.assertj.core.api.StringAssert;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.html.HtmlEscapers;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.ServiceProvider;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.i18n.LocalizableString;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.openidconnect.RedirectUri;
import oasis.security.KeyPairLoader;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.authz.KeysEndpoint;
import oasis.web.view.HandlebarsBodyWriter;

@RunWith(JukitoRunner.class)
public class LogoutPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LogoutPage.class);

      bind(JsonFactory.class).to(JacksonFactory.class);

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());
    }
  }

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
    setAccountId("accountId");
  }};

  private static final ServiceProvider serviceProvider = new ServiceProvider() {{
    setId("service provider");
    setName(new LocalizableString("Test Service Provider"));
    setPost_logout_redirect_uris(Collections.singletonList("https://application/after_logout"));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(ApplicationRepository applicationRepository) {
    when(applicationRepository.getServiceProvider(serviceProvider.getId())).thenReturn(serviceProvider);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LogoutPage.class);
    resteasy.getDeployment().getProviderFactory().register(HandlebarsBodyWriter.class);
  }
  @Test public void testLegacyGet_notLoggedIn_noContinueUrl() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(LoginPage.defaultContinueUrl(UriBuilder.fromUri(InProcessResteasy.BASE_URI)));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInThePast();
    }
  }

  @Test public void testGet_loggedIn_noIdTokenHint(TokenRepository tokenRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("post_logout_redirect_uri", "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        // post_logout_redirect_url should not be used
        .doesNotMatch(hiddenInput("continue", "http://www.google.com"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_withIdTokenHint(TokenRepository tokenRepository,
      OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    String idToken = IdToken.signUsingRsaSha256(settings.keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(InProcessResteasy.BASE_URI.toString())
            .setSubject(sidToken.getAccountId())
            .setAudience(serviceProvider.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", serviceProvider.getPost_logout_redirect_uris().get(0))
        .queryParam("state", "some+state")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .contains(serviceProvider.getName().get(Locale.ROOT))
        .matches(hiddenInput("continue", new RedirectUri(serviceProvider.getPost_logout_redirect_uris().get(0))
            .setState("some+state")
            .toString()));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_withIdTokenHintAndBadPostLogoutRedirectUri(TokenRepository tokenRepository,
      OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    String idToken = IdToken.signUsingRsaSha256(settings.keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(InProcessResteasy.BASE_URI.toString())
            .setSubject(sidToken.getAccountId())
            .setAudience(serviceProvider.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", "https://unregistered")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .contains(serviceProvider.getName().get(Locale.ROOT))
        .doesNotMatch(hiddenInput("continue", "https://unregistered"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_badIdTokenHint(TokenRepository tokenRepository) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    // See tests for the parseIdTokenHint method for what is and isn't a valid ID Token hint.
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", "invalid id_token_hint")
        .queryParam("post_logout_redirect_uri", "http://example.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .doesNotContain(serviceProvider.getName().get(Locale.ROOT))
        .doesNotMatch(hiddenInput("continue", "http://example.com"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_notLoggedIn_noIdTokenHint() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("post_logout_redirect_uri", "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    // post_logout_redirect_url should not be used
    assertThat(response.getLocation()).isEqualTo(LoginPage.defaultContinueUrl(UriBuilder.fromUri(InProcessResteasy.BASE_URI)));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
  }

  @Test public void testGet_notLoggedIn_withIdTokenHint(OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(settings.keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(InProcessResteasy.BASE_URI.toString())
            .setSubject(sidToken.getAccountId())
            .setAudience(serviceProvider.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", serviceProvider.getPost_logout_redirect_uris().get(0))
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create(serviceProvider.getPost_logout_redirect_uris().get(0)));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
  }

  @Test public void testGet_notLoggedIn_withIdTokenHintAndBadPostLogoutRedirectUri(
      OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    String idToken = IdToken.signUsingRsaSha256(settings.keyPair.getPrivate(), jsonFactory,
        new IdToken.Header()
            .setType("JWS")
            .setAlgorithm("RS256")
            .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID),
        new IdToken.Payload()
            .setIssuer(InProcessResteasy.BASE_URI.toString())
            .setSubject(sidToken.getAccountId())
            .setAudience(serviceProvider.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", "https://unregistered")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isNotEqualTo(URI.create("https://unregistered"));
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
  }

  private StringAssert assertLogoutPage(Response response) {
    assertThat(response.getMediaType().toString()).startsWith(MediaType.TEXT_HTML);
    // XXX: this is really poor-man's checking. We should use the DOM (through Cucumber/Capybara, or an HTML5 parser)
    return assertThat(response.readEntity(String.class))
        .matches("(?s).*\\baction=([\"']?)" + Pattern.quote(UriBuilder.fromResource(LogoutPage.class).build().toString()) + "\\1[\\s>].*");
  }

  private String hiddenInput(String name, @Nullable String value) {
    return "(?s).*<input[^>]+type=([\"']?)hidden\\1[^>]+name=([\"']?)"
        + Pattern.quote(HtmlEscapers.htmlEscaper().escape(name))
        + "(\\2)[^>]+value=([\"']?)"
        + (value == null ? "[^\"]*" : Pattern.quote(HtmlEscapers.htmlEscaper().escape(value)))
        + "\\3[\\s>].*";
  }
}
