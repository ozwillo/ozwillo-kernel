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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
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
import oasis.services.authn.TokenSerializer;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LoginSoyInfo;
import oasis.soy.templates.SignUpSoyInfo;
import oasis.urls.Urls;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/a/signup")
public class SignUpPage {
  private static Logger logger = LoggerFactory.getLogger(SignUpPage.class);

  public static final String CONTINUE_PARAM = "continue";

  @Inject AccountRepository accountRepository;
  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject CredentialsRepository credentialsRepository;
  @Inject TokenHandler tokenHandler;
  @Inject Urls urls;
  @Inject MailSender mailSender;
  @Inject LocaleHelper localeHelper;
  @Inject AuthModule.Settings authSettings;
  @Inject ServiceRepository serviceRepository;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;
  @Context Request request;

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response signUp(
      @Context HttpHeaders headers,
      @FormParam(CONTINUE_PARAM) URI continueUrl,
      @FormParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("email") String email,
      @FormParam("pwd") String password,
      @FormParam("nickname") String nickname
  ) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(urls.myOasis(), uriInfo);
    }
    locale = localeHelper.selectLocale(locale, request);

    if (Strings.isNullOrEmpty(email) || Strings.isNullOrEmpty(password) || Strings.isNullOrEmpty(nickname)) {
      return LoginPage.signupForm(Response.ok(), continueUrl, locale, authSettings, LoginPage.SignupError.MISSING_REQUIRED_FIELD);
    }
    // TODO: Verify that the password as a sufficiently strong length or even a strong entropy
    if (password.length() < authSettings.passwordMinimumLength) {
      return LoginPage.signupForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, locale, authSettings, LoginPage.SignupError.PASSWORD_TOO_SHORT);
    }

    UserAccount account = new UserAccount();
    account.setEmail_address(email);
    account.setNickname(nickname);
    account.setLocale(locale);
    // TODO: Use a zoneinfo "matching" the selected locale
    account.setZoneinfo("Europe/Paris");
    account = accountRepository.createUserAccount(account);
    if (account == null) {
      // TODO: Allow the user to retrieve their password
      return LoginPage.signupForm(Response.ok(), continueUrl, locale, authSettings, LoginPage.SignupError.ACCOUNT_ALREADY_EXISTS);
    } else {
      userPasswordAuthenticator.setPassword(account.getId(), password);
    }

    // TODO: send email asynchronously
    try {
      String pass = tokenHandler.generateRandom();
      AccountActivationToken accountActivationToken = tokenHandler.createAccountActivationToken(account.getId(), extractContinueUrl(continueUrl), pass);
      URI activationLink = uriInfo.getBaseUriBuilder()
          .path(ActivateAccountPage.class)
          .queryParam(LoginPage.LOCALE_PARAM, account.getLocale().toLanguageTag())
          .build(TokenSerializer.serialize(accountActivationToken, pass));

      mailSender.send(new MailMessage()
          .setRecipient(email, nickname)
          .setLocale(account.getLocale())
          .setSubject(SignUpSoyInfo.ACTIVATE_ACCOUNT_SUBJECT)
          .setBody(SignUpSoyInfo.ACTIVATE_ACCOUNT)
          .setHtml()
          .setData(new SoyMapData(
              SignUpSoyInfo.ActivateAccountSoyTemplateInfo.NICKNAME, nickname,
              SignUpSoyInfo.ActivateAccountSoyTemplateInfo.ACTIVATION_LINK, activationLink.toString(),
              SignUpSoyInfo.ActivateAccountSoyTemplateInfo.PORTAL_URL, LoginPage.defaultContinueUrl(urls.landingPage(), uriInfo).toString()
          )));
      // TODO: redirect to a bookmarkable URI (with form to resend the activation mail)
      return Response.ok()
          .entity(new SoyTemplate(LoginSoyInfo.ACCOUNT_PENDING_ACTIVATION, account.getLocale()))
          .build();
    } catch (MessagingException e) {
      logger.error("Error sending activation email", e);
      accountRepository.deleteUserAccount(account.getId());
      credentialsRepository.deleteCredentials(ClientType.USER, account.getId());
      return LoginPage.signupForm(Response.ok(), continueUrl, locale, authSettings, LoginPage.SignupError.MESSAGING_ERROR);
    }
  }

  @Nullable
  private URI extractContinueUrl(URI continueUrl) {
    // FIXME: Quick and dirty hack for https://github.com/pole-numerique/oasis/issues/103
    MultivaluedMap<String, String> params = new ResteasyUriInfo(continueUrl).getQueryParameters();
    if (params.isEmpty() || !params.containsKey("client_id") || !params.containsKey("redirect_uri")) {
      return null;
    }
    List<String> client_id = params.get("client_id");
    List<String> redirect_uri = params.get("redirect_uri");
    if (client_id.size() != 1 || redirect_uri.size() != 1) {
      return null;
    }
    Service service = serviceRepository.getServiceByRedirectUri(client_id.get(0), redirect_uri.get(0));
    if (service == null || service.getService_uri() == null) {
      return null;
    }
    try {
      return new URI(service.getService_uri());
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
