/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.AbstractUriAssert;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import com.google.inject.Inject;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.auth.AuthModule;
import oasis.http.testing.InProcessResteasy;
import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AccountActivationToken;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.Urls;
import oasis.urls.UrlsModule;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.i18n.LocaleHelper;
import oasis.web.userdirectory.MembershipInvitationPage;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class SignUpPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(SignUpPage.class);

      install(new NoopAuditLogModule());
      install(new SoyGuiceModule());
      install(new UrlsModule(ImmutableUrls.builder()
          .landingPage(URI.create("https://oasis/landing-page"))
          .myOasis(URI.create("https://oasis/my"))
          .build()));
      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setPasswordMinimumLength(6)
          .build());

      bindMock(UserPasswordAuthenticator.class).in(TestSingleton.class);
      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(MailSender.class).in(TestSingleton.class);
    }
  }

  private static final UserAccount someUserAccount = new UserAccount() {{
    setId("someUser");
    setEmail_address("foo@example.com");
    setNickname("nick");
    setLocale(LocaleHelper.DEFAULT_LOCALE);
  }};
  private static final AccountActivationToken someActivationToken = new AccountActivationToken() {{
    setId("someActivationToken");
    setAccountId(someUserAccount.getId());
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Inject Urls urls;

  @Before public void setupMocks(AccountRepository accountRepository, TokenHandler tokenHandler) {
    when(accountRepository.createUserAccount(refEq(someUserAccount, "id"), eq(false)))
        .thenReturn(someUserAccount);

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createAccountActivationToken(eq(someActivationToken.getAccountId()), nullable(URI.class), eq("pass")))
        .thenReturn(someActivationToken);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(SignUpPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test public void simpleSignUp(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, urls.landingPage().get().toString())
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertPendingActivationPage(response);

    verify(userPasswordAuthenticator).setPassword(someUserAccount.getId(), "password");
    assertActivationTokenContinueUrl(tokenHandler).isNull();
  }

  @Test public void missingValues(AccountRepository accountRepository, UserPasswordAuthenticator userPasswordAuthenticator,
      TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, urls.landingPage().get().toString())
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertSignUpForm(response).contains("Some required fields are not filled.");

    verifyZeroInteractions(accountRepository, userPasswordAuthenticator, tokenHandler);
  }

  @Test public void passwordTooShort(AccountRepository accountRepository, UserPasswordAuthenticator userPasswordAuthenticator,
      TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, urls.landingPage().get().toString())
            .param("email", "foo@example.com")
            .param("pwd", "pass")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertSignUpForm(response).contains("Password must be at least 6 characters long.");

    verifyZeroInteractions(accountRepository, userPasswordAuthenticator, tokenHandler);
  }

  @Test public void accountAlreadyExists(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, urls.landingPage().get().toString())
            .param("email", "bar@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertSignUpForm(response).contains("An account with that email address already exists.");

    verifyZeroInteractions(userPasswordAuthenticator, tokenHandler);
  }

  @Test public void messagingError(MailSender mailSender, AccountRepository accountRepository, CredentialsRepository credentialsRepository) throws MessagingException {
    doThrow(MessagingException.class).when(mailSender).send(any(MailMessage.class));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, urls.landingPage().get().toString())
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertSignUpForm(response).contains("Check your email address and try again in a few minutes.");

    verify(accountRepository).deleteUserAccount(someUserAccount.getId());
    verify(credentialsRepository).deleteCredentials(ClientType.USER, someUserAccount.getId()); 
  }

  @Test public void extractServiceUriFromContinueUrl(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler,
      ServiceRepository serviceRepository) {
    Service service = new Service() {{
      setService_uri("http://foo/");
    }};
    when(serviceRepository.getServiceByRedirectUri("appInstanceId", "http://foo/callback"))
        .thenReturn(service);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class)
                .queryParam("client_id", "appInstanceId")
                .queryParam("redirect_uri", "http://foo/callback")
                .build().toString())
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertPendingActivationPage(response);

    verify(userPasswordAuthenticator).setPassword(someUserAccount.getId(), "password");
    assertActivationTokenContinueUrl(tokenHandler).isEqualTo(URI.create(service.getService_uri()));
  }

  @Test public void storeContinueUrlIfLocal(UserPasswordAuthenticator userPasswordAuthenticator, TokenHandler tokenHandler,
      ServiceRepository serviceRepository) {
    final URI continueUrl = resteasy.getBaseUriBuilder().path(MembershipInvitationPage.class)
        .build("membershipInvitationToken");
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, continueUrl.toString())
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertPendingActivationPage(response);

    verify(userPasswordAuthenticator).setPassword(someUserAccount.getId(), "password");
    assertActivationTokenContinueUrl(tokenHandler).isEqualTo(continueUrl);
    verifyZeroInteractions(serviceRepository);
  }

  @Test public void doNotStoreContinueUrlIfNotLocal(UserPasswordAuthenticator userPasswordAuthenticator,
      TokenHandler tokenHandler, ServiceRepository serviceRepository) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(SignUpPage.class))
        .request().post(Entity.form(new Form()
            .param(SignUpPage.CONTINUE_PARAM, "http://phish/")
            .param("email", "foo@example.com")
            .param("pwd", "password")
            .param("nickname", "nick")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertPendingActivationPage(response);

    verify(userPasswordAuthenticator).setPassword(someUserAccount.getId(), "password");
    assertActivationTokenContinueUrl(tokenHandler).isNull();
    verifyZeroInteractions(serviceRepository);
  }

  private AbstractUriAssert<?> assertActivationTokenContinueUrl(TokenHandler tokenHandler) {
    ArgumentCaptor<URI> continueUrl = ArgumentCaptor.forClass(URI.class);
    verify(tokenHandler).createAccountActivationToken(anyString(), continueUrl.capture(), anyString());
    return assertThat(continueUrl.getValue());
  }

  private AbstractCharSequenceAssert<?, String> assertSignUpForm(Response response) {
    assertThat(response.getMediaType().toString()).startsWith(MediaType.TEXT_HTML);
    // XXX: this is really poor-man's checking. We should use the DOM (through Selenium, or an HTML5 parser)
    return assertThat(response.readEntity(String.class))
        .matches("(?s).*\\baction=([\"']?)" + Pattern.quote(UriBuilder.fromResource(SignUpPage.class).build().toString()) + "\\1[\\s>].*");
  }

  private void assertPendingActivationPage(Response response) {
    assertThat(response.getMediaType().toString()).startsWith(MediaType.TEXT_HTML);
    assertThat(response.readEntity(String.class)).contains("Account pending activation");
  }
}
