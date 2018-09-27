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
package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.auth.FranceConnectModule;
import oasis.auth.ImmutableFranceConnectModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyGuiceModule;
import oasis.urls.ImmutableBaseUrls;
import oasis.urls.ImmutablePathUrls;
import oasis.urls.UrlsModule;
import oasis.web.authn.franceconnect.FranceConnectCallback;
import oasis.web.authn.franceconnect.FranceConnectLoginState;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.SoyTemplateBodyWriter;
import okhttp3.HttpUrl;

@RunWith(JukitoRunner.class)
public class LoginPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LoginPage.class);

      install(new NoopAuditLogModule());
      install(new SoyGuiceModule());
      install(new UrlsModule(ImmutableBaseUrls.builder().build(), ImmutablePathUrls.builder().build()));
      install(new FranceConnectModule(ImmutableFranceConnectModule.Settings.builder()
          .issuer("https://fcp/")
          .authorizationEndpoint(HttpUrl.parse("https://fcp/authorize"))
          .tokenEndpoint("https://fcp/token")
          .userinfoEndpoint("https://fcp/userinfo")
          .endSessionEndpoint(HttpUrl.parse("https://fcp/logout"))
          .clientId("fcp_client_id")
          .clientSecret("fcp_client_secret")
          .build()));

      bindMock(UserPasswordAuthenticator.class).in(TestSingleton.class);
      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(SessionManagementHelper.class).in(TestSingleton.class);
      bindMock(ClientCertificateHelper.class).in(TestSingleton.class);
    }
  }

  static final String cookieName = CookieFactory.getCookieName(UserFilter.COOKIE_NAME, true);
  static final String browserStateCookieName = CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, true);

  private static final UserAccount someUserAccount = new UserAccount() {{
    setId("someUser");
    setEmail_address("some@example.com");
  }};
  private static final UserAccount otherUserAccount = new UserAccount() {{
    setId("otherUser");
    setEmail_address("other@example.com");
  }};
  private static final UserAccount passwordlessAccount = new UserAccount() {{
    setId("passwordless");
    setEmail_address("other@example.com");
    setFranceconnect_sub("franceconnect_sub");
    setLocale(ULocale.ITALY);
  }};
  private static final SidToken someSidToken = new SidToken() {{
    setId("someSidToken");
    setAccountId(someUserAccount.getId());
    expiresIn(Duration.ofHours(1));
  }};
  private static final SidToken otherSidToken = new SidToken() {{
    setId("otherSidToken");
    setAccountId(otherUserAccount.getId());
    expiresIn(Duration.ofHours(1));
  }};
  private static final SidToken passwordlessSidToken = new SidToken() {{
    setId("passwordlessSidToken");
    setAccountId(passwordlessAccount.getId());
    setFranceconnectIdToken("franceconnect_id_token");
    setFranceconnectAccessToken("franceconnect_access_token");
    expiresIn(Duration.ofHours(1));
  }};
  private static final ClientCertificate someClientCertificate = new ClientCertificate() {{
    setId("some certificate");
    setSubject_dn("valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id(someUserAccount.getId());
  }};

  private static final ClientCertificate serviceClientCertificate = new ClientCertificate() {{
    setId("service certificate");
    setSubject_dn("service subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.PROVIDER);
    setClient_id("service");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @SuppressWarnings("unchecked")
  @Before public void setupMocks(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler, SessionManagementHelper sessionManagementHelper,
      AccountRepository accountRepository, TokenRepository tokenRepository, CredentialsRepository credentialsRepository,
      BrandRepository brandRepository) throws LoginException {
    when(userPasswordAuthenticator.authenticate(someUserAccount.getEmail_address(), "password")).thenReturn(someUserAccount);
    when(userPasswordAuthenticator.authenticate(someUserAccount.getEmail_address(), "invalid")).thenThrow(FailedLoginException.class);
    when(userPasswordAuthenticator.authenticate(otherUserAccount.getEmail_address(), "password")).thenReturn(otherUserAccount);
    when(userPasswordAuthenticator.authenticate(eq("unknown@example.com"), anyString())).thenThrow(AccountNotFoundException.class);

    when(credentialsRepository.getCredentials(ClientType.USER, someUserAccount.getId())).thenReturn(new Credentials() {{
      setClientType(ClientType.USER);
      setId(someUserAccount.getId());
    }});
    when(credentialsRepository.getCredentials(ClientType.USER, otherUserAccount.getId())).thenReturn(new Credentials() {{
      setClientType(ClientType.USER);
      setId(otherUserAccount.getId());
    }});

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createSidToken(eq(someUserAccount.getId()), any(byte[].class), anyBoolean(), isNull(), isNull(), eq("pass"))).thenReturn(someSidToken);
    when(tokenHandler.createSidToken(eq(otherUserAccount.getId()), any(byte[].class), anyBoolean(), isNull(), isNull(), eq("pass"))).thenReturn(otherSidToken);

    when(accountRepository.getUserAccountById(someUserAccount.getId())).thenReturn(someUserAccount);
    when(accountRepository.getUserAccountById(otherUserAccount.getId())).thenReturn(otherUserAccount);
    when(accountRepository.getUserAccountById(passwordlessAccount.getId())).thenReturn(passwordlessAccount);

    when(tokenRepository.reAuthSidToken(anyString())).thenReturn(true);

    when(sessionManagementHelper.generateBrowserState()).thenReturn("browser-state");
    when(brandRepository.getBrandInfo(any())).thenReturn(new BrandInfo());
  }

  @After public void verifyMocks(SessionManagementHelper sessionManagementHelper) {
    verify(sessionManagementHelper, never()).computeSessionState(anyString(), anyString(), anyString());
  }

  @Before public void setUp() throws Exception {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LoginPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test public void loginPageWithDefaults() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response);
  }

  @Test public void loginPageWithContinue() {
    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .queryParam(LoginPage.CONTINUE_PARAM, UrlEscapers.urlFormParameterEscaper().escape(continueUrl))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .doesNotMatch(hiddenInput("cancel", null))
        .doesNotContain(">Cancel<");
  }

  @SuppressWarnings("unchecked")
  @Test public void loginPageWithKnownCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(someClientCertificate);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(reauthUser(someUserAccount.getEmail_address()));
  }

  @Test public void loginPageWhileLoggedIn() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(reauthUser(someUserAccount.getEmail_address()));
  }

  @Test public void loginPageWhileLoggedInPasswordless(FranceConnectModule.Settings fcSettings) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(passwordlessSidToken));

    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .queryParam(LoginPage.CONTINUE_PARAM, UrlEscapers.urlFormParameterEscaper().escape(continueUrl))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);

    HttpUrl parsedLocation = HttpUrl.get(response.getLocation());
    assertThat(parsedLocation.newBuilder()
        .removeAllQueryParameters("response_type")
        .removeAllQueryParameters("client_id")
        .removeAllQueryParameters("redirect_uri")
        .removeAllQueryParameters("scope")
        .removeAllQueryParameters("state")
        .removeAllQueryParameters("nonce")
        .removeAllQueryParameters("id_token_hint")
        .build()).isEqualTo(fcSettings.authorizationEndpoint());
    assertThat(parsedLocation.queryParameter("response_type")).isEqualTo("code");
    assertThat(parsedLocation.queryParameter("client_id")).isEqualTo(fcSettings.clientId());
    assertThat(parsedLocation.queryParameter("redirect_uri"))
        .isEqualTo(resteasy.getBaseUriBuilder().path(FranceConnectCallback.class).build().toString());
    assertThat(parsedLocation.queryParameter("scope")).isEqualTo("openid profile birth email");
    assertThat(parsedLocation.queryParameter("id_token_hint")).isEqualTo(passwordlessSidToken.getFranceconnectIdToken());
    String state = parsedLocation.queryParameter("state");
    assertThat(state).isNotNull();
    String nonce = parsedLocation.queryParameter("nonce");
    assertThat(nonce).isNotNull();

    assertThat(response.getCookies())
        .doesNotContainKeys(cookieName, browserStateCookieName)
        .containsEntry(
            FranceConnectLoginState.getCookieName(state, true),
            FranceConnectLoginState.createCookie(state, passwordlessAccount.getLocale(), nonce, BrandInfo.DEFAULT_BRAND, URI.create(continueUrl), true));
  }

  @SuppressWarnings("unchecked")
  @Test public void loginPageWhileLoggedInWithMismatchingCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(someClientCertificate);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(otherSidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(reauthUser(otherUserAccount.getEmail_address()));
  }

  @SuppressWarnings("unchecked")
  @Test public void loginPageWhileLoggedInWithServiceCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(serviceClientCertificate);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(reauthUser(someUserAccount.getEmail_address()));
  }

  @Test public void signIn(TokenHandler tokenHandler) {
    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUri().resolve(continueUrl));
    assertThat(response.getCookies())
        .containsEntry(cookieName, CookieFactory.createSessionCookie(
            UserFilter.COOKIE_NAME, TokenSerializer.serialize(someSidToken, "pass"), true, true))
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createSidToken(eq(someUserAccount.getId()), any(byte[].class), eq(false), isNull(), isNull(), anyString());
  }

  @SuppressWarnings("unchecked")
  @Test public void signInWithCertificate(ClientCertificateHelper clientCertificateHelper, TokenHandler tokenHandler) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(someClientCertificate);

    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUri().resolve(continueUrl));
    assertThat(response.getCookies())
        .containsEntry(cookieName, CookieFactory.createSessionCookie(
            UserFilter.COOKIE_NAME, TokenSerializer.serialize(someSidToken, "pass"), true, true))
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createSidToken(eq(someUserAccount.getId()), any(byte[].class), eq(true), isNull(), isNull(), anyString());
  }

  @SuppressWarnings("unchecked")
  @Test public void signInWithMismatchingCertificate(ClientCertificateHelper clientCertificateHelper, TokenHandler tokenHandler) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(someClientCertificate);

    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", otherUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUri().resolve(continueUrl));
    assertThat(response.getCookies())
        .containsEntry(cookieName, CookieFactory.createSessionCookie(
            UserFilter.COOKIE_NAME, TokenSerializer.serialize(otherSidToken, "pass"), true, true))
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createSidToken(eq(otherUserAccount.getId()), any(byte[].class), eq(false), isNull(), isNull(), anyString());
  }

  @SuppressWarnings("unchecked")
  @Test public void signInWithServiceCertificate(ClientCertificateHelper clientCertificateHelper, TokenHandler tokenHandler) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(serviceClientCertificate);

    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUri().resolve(continueUrl));
    assertThat(response.getCookies())
        .containsEntry(cookieName, CookieFactory.createSessionCookie(
            UserFilter.COOKIE_NAME, TokenSerializer.serialize(someSidToken, "pass"), true, true))
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createSidToken(eq(someUserAccount.getId()), any(byte[].class), eq(false), isNull(), isNull(), anyString());
  }

  @Test public void trySignInWithBadPassword() {
    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "invalid")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .doesNotMatch(hiddenInput("cancel", null))
        .doesNotContain(">Cancel<")
        .contains("Incorrect email address or password");
  }

  @Test public void trySignInWithUnknownAccount() {
    final String continueUrl = "/foo/bar?qux=quux";
    final String cancelUrl = "https://application/callback=state=state&error=login_required";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("cancel", cancelUrl)
            .param("u", "unknown@example.com")
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .contains("Incorrect email address or password");
  }

  @Test public void signInWhileLoggedIn(TokenRepository tokenRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUri().resolve(continueUrl));
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);

    verify(tokenRepository, never()).revokeToken(anyString());
    verify(tokenRepository).reAuthSidToken(someSidToken.getId());
  }

  @Test public void signInWhileLoggedInWithOtherUser() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", otherUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertLoginForm(response)
        .matches(reauthUser(someUserAccount.getEmail_address()));
  }

  private AbstractCharSequenceAssert<?, String> assertLoginForm(Response response) {
    assertThat(response.getMediaType().toString()).startsWith(MediaType.TEXT_HTML);
    // XXX: this is really poor-man's checking. We should use the DOM (through Cucumber/Capybara, or an HTML5 parser)
    return assertThat(response.readEntity(String.class))
        .matches("(?s).*\\baction=([\"']?)" + Pattern.quote(UriBuilder.fromResource(LoginPage.class).build().toString()) + "\\1[\\s>].*");
  }

  private String reauthUser(String user) {
    return "(?s).*>\\s*"
        + Pattern.quote(HtmlEscapers.htmlEscaper().escape(user))
        + "\\s*</.*";
  }

  private String hiddenInput(String name, @Nullable String value) {
    return "(?s).*<input[^>]+type=([\"']?)hidden\\1[^>]+name=([\"']?)"
        + Pattern.quote(HtmlEscapers.htmlEscaper().escape(name))
        + "(\\2)[^>]+value=([\"']?)"
        + (value == null ? "[^\"]*" : Pattern.quote(HtmlEscapers.htmlEscaper().escape(value)))
        + "\\3[\\s>].*";
  }

  private String link(String href) {
    return "(?s).*<a[^>]+href=([\"']?)" + Pattern.quote(HtmlEscapers.htmlEscaper().escape(href)) + "\\1[\\s>].*";
  }
}
