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
import java.security.SecureRandom;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.security.auth.login.LoginException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.auth.AuthModule;
import oasis.auth.FranceConnectModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LoginSoyInfo;
import oasis.soy.templates.LoginSoyInfo.LoginSoyTemplateInfo;
import oasis.soy.templates.LoginSoyInfo.ReauthSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.StaticResources;
import oasis.web.authn.franceconnect.FranceConnectLogin;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@User
@Path("/a/login")
public class LoginPage {
  private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

  public static final String CONTINUE_PARAM = "continue";
  public static final String CANCEL_PARAM = "cancel";
  public static final String LOCALE_PARAM = "hl";

  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AccountRepository accountRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject AuditLogService auditLogService;
  @Inject Urls urls;
  @Inject LocaleHelper localeHelper;
  @Inject ClientCertificateHelper clientCertificateHelper;
  @Inject LoginHelper loginHelper;
  @Inject @Nullable FranceConnectModule.Settings franceConnectSettings;
  @Inject SecureRandom secureRandom;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @QueryParam(CONTINUE_PARAM) URI continueUrl,
      @QueryParam(LOCALE_PARAM) @Nullable ULocale locale
  ) {
    return loginOrReauthForm(Response.ok(), continueUrl, localeHelper.selectLocale(locale, request), null);
  }

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response post(
      @FormParam(LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam("continue") URI continueUrl
  ) {
    locale = localeHelper.selectLocale(locale, request);

    if (userName.isEmpty()) {
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, locale, null);
    }
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    UserAccount account;
    try {
      account = userPasswordAuthenticator.authenticate(userName, password);
    } catch (LoginException e) {
      log(userName, LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, locale, LoginError.INCORRECT_USERNAME_OR_PASSWORD);
    }

    if (securityContext.getUserPrincipal() != null) {
      return reAuthenticate(userName, account, continueUrl);
    }

    return loginHelper.authenticate(account, headers, securityContext, continueUrl, null, null, () -> {
      log(auditLogService, userName, LoginPage.LoginLogEvent.LoginResult.AUTHENTICATION_SUCCEEDED);
    }).build();
  }

  private Response reAuthenticate(String userName, UserAccount account, URI continueUrl) {
    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    if (!account.getId().equals(sidToken.getAccountId())) {
      // Form has been tampered with, or user signed in with another account since the form was generated
      // Re-display the form: if user signed out/in, it will show the new (current) user.
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, account.getLocale(), null);
    }
    if (!tokenRepository.reAuthSidToken(sidToken.getId())) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("Error when updating SidToken {} for Account {}.", sidToken.getId(), account.getId());
      return Response.serverError().build();
    }

    log(userName, LoginLogEvent.LoginResult.RE_ENTER_PASSWORD_SUCCEEDED);

    return Response.seeOther(continueUrl).build();
  }

  private Response loginOrReauthForm(Response.ResponseBuilder builder, @Nullable URI continueUrl, @Nullable ULocale locale, @Nullable LoginError error) {
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    if (securityContext.getUserPrincipal() != null) {
      SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
      UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());
      // XXX: what if account is null?
      return reauthForm(builder, continueUrl, error, account);
    }

    final ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(headers.getRequestHeaders());
    if (clientCertificate != null && clientCertificate.getClient_type() == ClientType.USER) {
      UserAccount account = accountRepository.getUserAccountById(clientCertificate.getClient_id());
      if (account != null) {
        return reauthForm(builder, continueUrl, error, account);
      }
    }

    return loginForm(builder, continueUrl, locale, franceConnectSettings != null, error,
        BrandHelper.getBrandIdFromUri(uriInfo));
  }

  private Response reauthForm(Response.ResponseBuilder builder, URI continueUrl, @Nullable LoginError error, UserAccount userAccount) {
    if (credentialsRepository.getCredentials(ClientType.USER, userAccount.getId()) == null) {
      assert franceConnectSettings != null;
      assert userAccount.getFranceconnect_sub() != null;
      return FranceConnectLogin.redirectToFranceConnect(franceConnectSettings, secureRandom, securityContext, uriInfo, userAccount.getLocale(), continueUrl);
    }
    assert userAccount.getEmail_address() != null;

    ImmutableMap.Builder<String, String> data = ImmutableMap.<String, String>builderWithExpectedSize(4)
        .put(ReauthSoyTemplateInfo.REAUTH_EMAIL, userAccount.getEmail_address())
        .put(ReauthSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(LoginPage.class).build().toString())
        .put(ReauthSoyTemplateInfo.CONTINUE, continueUrl.toString());
    if (error != null) {
      data.put(ReauthSoyTemplateInfo.ERROR, error.name());
    }

    return buildResponseFromView(builder, new SoyTemplate(LoginSoyInfo.REAUTH, userAccount.getLocale(), data.build(),
        BrandHelper.getBrandIdFromUri(uriInfo)));
  }

  private static Response loginForm(Response.ResponseBuilder builder, URI continueUrl, ULocale locale, boolean withFranceConnect, @Nullable LoginError error, String brandId) {
    return loginAndSignupForm(builder, continueUrl, locale, null, withFranceConnect, error, brandId);
  }

  static Response signupForm(Response.ResponseBuilder builder, URI continueUrl, ULocale locale, AuthModule.Settings authSettings, boolean withFranceConnect, @Nullable SignupError error, String brandId) {
    return loginAndSignupForm(builder, continueUrl, locale, authSettings, withFranceConnect, error, brandId);
  }

  private static Response loginAndSignupForm(Response.ResponseBuilder builder, URI continueUrl,
      ULocale locale, @Nullable AuthModule.Settings authSettings, boolean withFranceConnect, @Nullable Enum<?> error, String brandId) {
    ImmutableMap.Builder<String, String> localeUrlMap = ImmutableMap.builderWithExpectedSize(LocaleHelper.SUPPORTED_LOCALES.size());
    for (ULocale supportedLocale : LocaleHelper.SUPPORTED_LOCALES) {
      String languageTag = supportedLocale.toLanguageTag();
      URI uri = UriBuilder.fromResource(LoginPage.class)
          .queryParam(LoginPage.CONTINUE_PARAM, continueUrl)
          .queryParam(LoginPage.LOCALE_PARAM, languageTag)
          .build();
      localeUrlMap.put(languageTag, uri.toString());
    }

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builder()
        .put(LoginSoyTemplateInfo.SIGN_UP_FORM_ACTION, UriBuilder.fromResource(SignUpPage.class).build().toString())
        .put(LoginSoyTemplateInfo.LOGIN_FORM_ACTION, UriBuilder.fromResource(LoginPage.class).build().toString())
        .put(LoginSoyTemplateInfo.FORGOT_PASSWORD, UriBuilder.fromResource(ForgotPasswordPage.class).queryParam(LOCALE_PARAM, locale.toLanguageTag()).build().toString())
        .put(LoginSoyTemplateInfo.CONTINUE, continueUrl.toString())
        .put(LoginSoyTemplateInfo.LOCALE_URL_MAP, localeUrlMap.build());
    if (withFranceConnect) {
      data.put(LoginSoyTemplateInfo.FRANCECONNECT, UriBuilder.fromResource(FranceConnectLogin.class).build().toString());
    }
    if (error != null) {
      data.put(LoginSoyTemplateInfo.ERROR, error.name());
    }
    if (authSettings != null) {
      data.put(LoginSoyTemplateInfo.PWD_MIN_LENGTH, authSettings.passwordMinimumLength);
    }

    return buildResponseFromView(builder, new SoyTemplate(LoginSoyInfo.LOGIN, locale, data.build(),
        brandId));
  }

  private static Response buildResponseFromView(Response.ResponseBuilder builder, SoyTemplate soyTemplate) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(soyTemplate)
        .build();
  }

  private URI defaultContinueUrl() {
    return defaultContinueUrl(urls.landingPage(), uriInfo);
  }

  public static URI defaultContinueUrl(Optional<URI> landingPage, UriInfo uriInfo) {
    return landingPage.orElseGet(() -> uriInfo.getBaseUriBuilder().path(StaticResources.class).path(StaticResources.class, "home").build());
  }

  private void log(String userName, LoginLogEvent.LoginResult loginResult) {
    log(auditLogService, userName, loginResult);
  }

  public static void log(AuditLogService auditLogService, String userName, LoginLogEvent.LoginResult loginResult) {
    auditLogService.event(LoginLogEvent.class)
        .setUserName(userName)
        .setLoginResult(loginResult)
        .log();
  }

  public static class LoginLogEvent extends AuditLogEvent {
    public LoginLogEvent() {
      super("login_event");
    }

    public LoginLogEvent setUserName(String userName) {
      this.addContextData("user_name", userName);
      return this;
    }

    public LoginLogEvent setLoginResult(LoginResult loginResult) {
      this.addContextData("login_result", loginResult);
      return this;
    }

    public static enum LoginResult {
      AUTHENTICATION_SUCCEEDED,
      AUTHENTICATION_FAILED,
      RE_ENTER_PASSWORD_SUCCEEDED,
    }
  }

  enum LoginError {
    INCORRECT_USERNAME_OR_PASSWORD
  }

  enum SignupError {
    MISSING_REQUIRED_FIELD,
    ACCOUNT_ALREADY_EXISTS,
    MESSAGING_ERROR,
    PASSWORD_TOO_SHORT,
  }
}
