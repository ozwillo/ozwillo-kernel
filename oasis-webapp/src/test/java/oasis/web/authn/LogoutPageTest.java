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
import com.google.common.collect.Iterables;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.i18n.LocalizableString;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.openidconnect.RedirectUri;
import oasis.security.KeyPairLoader;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.authz.KeysEndpoint;
import oasis.soy.SoyGuiceModule;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class LogoutPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LogoutPage.class);

      install(new SoyGuiceModule());

      bind(JsonFactory.class).to(JacksonFactory.class);

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .setLandingPage(URI.create("https://oasis/landing-page"))
          .build());
    }
  }

  private static final UserAccount account = new UserAccount() {{
    setId("accountId");
    setNickname("Nickname");
    setLocale(Locale.ROOT);
  }};

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
    setAccountId(account.getId());
  }};

  private static final AppInstance appInstance = new AppInstance() {{
    setId("appInstance");
    setName(new LocalizableString("Test Application Instance"));
  }};

  private static final Service service = new Service() {{
    setId("service provider");
    setInstance_id(appInstance.getId());
    setName(new LocalizableString("Test Service"));
    setService_uri("https://application/service");
    setPost_logout_redirect_uris(Collections.singleton("https://application/after_logout"));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AccountRepository accountRepository, AppInstanceRepository appInstanceRepository, ServiceRepository serviceRepository) {
    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);

    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);
    when(appInstanceRepository.getAppInstances(anyCollectionOf(String.class))).thenReturn(Collections.<AppInstance>emptyList());

    when(serviceRepository.getServiceByPostLogoutRedirectUri(appInstance.getId(), Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .thenReturn(service);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LogoutPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }
  @Test public void testGet_notLoggedIn_noParam(OpenIdConnectModule.Settings settings) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(settings.landingPage);
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
            .setAudience(appInstance.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .queryParam("state", "some&state")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(UserFilter.COOKIE_NAME)) {
      assertThat(response.getCookies().get(UserFilter.COOKIE_NAME).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .contains(appInstance.getName().get(Locale.ROOT))
        .contains(service.getService_uri())
        .matches(hiddenInput("continue", new RedirectUri(Iterables.getOnlyElement(service.getPost_logout_redirect_uris()))
            .setState("some&state")
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
            .setAudience(appInstance.getId())
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
        .contains(appInstance.getName().get(Locale.ROOT))
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
        .doesNotContain(appInstance.getName().get(Locale.ROOT))
        .doesNotContain(service.getService_uri())
        .doesNotMatch(hiddenInput("continue", "http://example.com"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_notLoggedIn_noIdTokenHint(OpenIdConnectModule.Settings settings) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("post_logout_redirect_uri", "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    // post_logout_redirect_url should not be used
    assertThat(response.getLocation()).isEqualTo(settings.landingPage);
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
            .setAudience(appInstance.getId())
    );
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())));
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
            .setAudience(appInstance.getId())
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
