package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.joda.time.Duration;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.TestingSoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.Urls;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class LoginPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LoginPage.class);

      install(new NoopAuditLogModule());
      install(new TestingSoyGuiceModule());

      bind(Urls.class).toInstance(ImmutableUrls.builder().build());

      bindMock(UserPasswordAuthenticator.class).in(TestSingleton.class);
      bindMock(TokenHandler.class).in(TestSingleton.class);
    }
  }

  private static final UserAccount someUserAccount = new UserAccount() {{
    setId("someUser");
    setEmail_address("some@example.com");
  }};
  private static final UserAccount otherUserAccount = new UserAccount() {{
    setId("otherUser");
    setEmail_address("other@example.com");
  }};
  private static final SidToken someSidToken = new SidToken() {{
    setId("someSidToken");
    setAccountId(someUserAccount.getId());
    expiresIn(Duration.standardHours(1));
  }};
  private static final SidToken otherSidToken = new SidToken() {{
    setId("otherSidToken");
    setAccountId(otherUserAccount.getId());
    expiresIn(Duration.standardHours(1));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setupMocks(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler,
      AccountRepository accountRepository, TokenRepository tokenRepository) throws LoginException {
    when(userPasswordAuthenticator.authenticate(someUserAccount.getEmail_address(), "password")).thenReturn(someUserAccount);
    when(userPasswordAuthenticator.authenticate(someUserAccount.getEmail_address(), "invalid")).thenThrow(FailedLoginException.class);
    when(userPasswordAuthenticator.authenticate(otherUserAccount.getEmail_address(), "password")).thenReturn(otherUserAccount);
    when(userPasswordAuthenticator.authenticate(eq("unknown@example.com"), anyString())).thenThrow(AccountNotFoundException.class);

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createSidToken(eq(someUserAccount.getId()), any(byte[].class), eq("pass"))).thenReturn(someSidToken);
    when(tokenHandler.createSidToken(eq(otherUserAccount.getId()), any(byte[].class), eq("pass"))).thenReturn(otherSidToken);

    when(accountRepository.getUserAccountById(someUserAccount.getId())).thenReturn(someUserAccount);
    when(accountRepository.getUserAccountById(otherUserAccount.getId())).thenReturn(otherUserAccount);

    when(tokenRepository.reAuthSidToken(anyString())).thenReturn(true);
  }

  @Before public void setUp() throws Exception {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LoginPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test public void loginPageWithDefaults() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertLoginForm(response);
  }

  @Test public void loginPageWithContinue() {
    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .queryParam(LoginPage.CONTINUE_PARAM, UrlEscapers.urlFormParameterEscaper().escape(continueUrl))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .doesNotMatch(hiddenInput("cancel", null))
        .doesNotContain(">Cancel<");
  }

  @Test public void loginPageWhileLoggedIn() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertLoginForm(response)
        .matches(reauthUser(someUserAccount.getEmail_address()));
  }

  @Test public void signIn() {
    final String continueUrl = "/foo/bar?baz&qux=qu%26ux";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(InProcessResteasy.BASE_URI.resolve(continueUrl));
  }

  @Test public void trySignInWithBadPassword() {
    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "invalid")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .doesNotMatch(hiddenInput("cancel", null))
        .doesNotContain(">Cancel<")
        .contains("Incorrect email address or password");
  }

  @Test public void trySignInWithUnknownAccount() {
    final String continueUrl = "/foo/bar?qux=quux";
    final String cancelUrl = "https://application/callback=state=state&error=login_required";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("cancel", cancelUrl)
            .param("u", "unknown@example.com")
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertLoginForm(response)
        .matches(hiddenInput("continue", continueUrl))
        .contains("Incorrect email address or password");
  }

  @Test public void signInWhileLoggedIn(TokenRepository tokenRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", someUserAccount.getEmail_address())
            .param("pwd", "password")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(InProcessResteasy.BASE_URI.resolve(continueUrl));

    verify(tokenRepository, never()).revokeToken(anyString());
    verify(tokenRepository).reAuthSidToken(someSidToken.getId());
  }

  @Test public void signInWhileLoggedInWithOtherUser() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    final String continueUrl = "/foo/bar?qux=quux";

    Response response = resteasy.getClient().target(UriBuilder.fromResource(LoginPage.class))
        .request().post(Entity.form(new Form()
            .param("continue", continueUrl)
            .param("u", otherUserAccount.getEmail_address())
            .param("pwd", "password")));

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
