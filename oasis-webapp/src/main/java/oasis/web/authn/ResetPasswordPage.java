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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ChangePasswordToken;
import oasis.model.authn.ClientType;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.CredentialsService;
import oasis.services.authn.TokenHandler;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.ChangePasswordSoyInfo;
import oasis.soy.templates.RecoverSoyInfo;
import oasis.urls.Urls;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/a/resetpwd/{token}")
@Produces(MediaType.TEXT_HTML)
public class ResetPasswordPage {

  @Inject AccountRepository accountRepository;
  @Inject TokenHandler tokenHandler;
  @Inject TokenRepository tokenRepository;
  @Inject CredentialsService credentialsService;
  @Inject Urls urls;
  @Inject LocaleHelper localeHelper;

  @Context Request request;

  @PathParam("token") String token;

  @GET
  public Response get(
      @QueryParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale
  ) {
    locale = localeHelper.selectLocale(locale, request);

    ChangePasswordToken changePasswordToken = tokenHandler.getCheckedToken(token, ChangePasswordToken.class);
    if (changePasswordToken == null) {
      return ForgotPasswordPage.form(Response.status(Response.Status.NOT_FOUND), locale, ForgotPasswordPage.ForgotPasswordError.EXPIRED_LINK);
    }

    UserAccount account = accountRepository.getUserAccountById(changePasswordToken.getAccountId());
    if (account == null) {
      // Account has been deleted since the password-reset process started; consider the link as having expired.
      return ForgotPasswordPage.form(Response.status(Response.Status.NOT_FOUND), locale, ForgotPasswordPage.ForgotPasswordError.EXPIRED_LINK);
    }

    return form(Response.ok(), account, locale, null);
  }

  @POST @StrictReferer
  public Response post(
      @FormParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("newpwd") String newpwd
  ) {
    locale = localeHelper.selectLocale(locale, request);

    ChangePasswordToken changePasswordToken = tokenHandler.getCheckedToken(token, ChangePasswordToken.class);
    if (changePasswordToken == null) {
      return ForgotPasswordPage.form(Response.status(Response.Status.NOT_FOUND), locale, ForgotPasswordPage.ForgotPasswordError.EXPIRED_LINK);
    }

    UserAccount account = accountRepository.getUserAccountById(changePasswordToken.getAccountId());
    if (account == null) {
      // Account has been deleted since the password-reset process started; consider the link as having expired.
      return ForgotPasswordPage.form(Response.status(Response.Status.NOT_FOUND), locale, ForgotPasswordPage.ForgotPasswordError.EXPIRED_LINK);
    }

    if (Strings.isNullOrEmpty(newpwd)) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, locale, ResetPasswordError.MISSING_REQUIRED_FIELD);
    }

    credentialsService.setPassword(ClientType.USER, changePasswordToken.getAccountId(), newpwd);

    // revoke all sessions / tokens
    tokenRepository.revokeTokensForAccount(changePasswordToken.getAccountId());

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(ChangePasswordSoyInfo.PASSWORD_CHANGED,
            account.getLocale(),
            urls.myOasis()
                .map(url -> ImmutableMap.of(ChangePasswordSoyInfo.PasswordChangedSoyTemplateInfo.CONTINUE, url.toString()))
                .orElse(null)
            ))
        .build();
  }

  private Response form(Response.ResponseBuilder builder, UserAccount account, ULocale locale, @Nullable ResetPasswordError error) {
    ImmutableMap.Builder<String, String> data = ImmutableMap.<String, String>builderWithExpectedSize(4)
        .put(RecoverSoyInfo.ResetPasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ResetPasswordPage.class).build(token).toString())
        .put(RecoverSoyInfo.ResetPasswordSoyTemplateInfo.EMAIL_ADDRESS, account.getEmail_address())
        .put(RecoverSoyInfo.ResetPasswordSoyTemplateInfo.LOCALE, locale.toLanguageTag());
    if (error != null) {
      data.put(RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.ERROR, error.name());
    }

    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(RecoverSoyInfo.RESET_PASSWORD, account.getLocale(), data.build()))
        .build();
  }

  enum ResetPasswordError {
    MISSING_REQUIRED_FIELD
  }
}
