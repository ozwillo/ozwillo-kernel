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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
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
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ChangePasswordToken;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.RecoverMailSoyInfo;
import oasis.soy.templates.RecoverSoyInfo;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/a/recover")
public class ForgotPasswordPage {
  private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordPage.class);

  @Inject AccountRepository accountRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject MailSender mailSender;
  @Inject TokenHandler tokenHandler;
  @Inject LocaleHelper localeHelper;

  @Context UriInfo uriInfo;
  @Context Request request;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @QueryParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale
  ) {
    return form(Response.ok(), localeHelper.selectLocale(locale, request), null);
  }

  @POST @StrictReferer
  public Response post(
      @FormParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("u") String email
  ) {
    locale = localeHelper.selectLocale(locale, request);

    if (Strings.isNullOrEmpty(email)) {
      return form(Response.status(Response.Status.BAD_REQUEST), locale, ForgotPasswordError.MISSING_REQUIRED_FIELD);
    }

    UserAccount account = accountRepository.getUserAccountByEmail(email);
    try {
      if (account == null || credentialsRepository.getCredentials(ClientType.USER, account.getId()) == null) {
        mailSender.send(new MailMessage()
          .setRecipient(email, null)
          .setLocale(locale)
          .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT_SUBJECT)
          .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT)
          .setHtml());
      } else {
        String pass = tokenHandler.generateRandom();
        ChangePasswordToken changePasswordToken = tokenHandler.createChangePasswordToken(account.getId(), pass);
        URI resetPasswordLink = uriInfo.getBaseUriBuilder()
            .path(ResetPasswordPage.class)
            .queryParam(LoginPage.LOCALE_PARAM, locale.toLanguageTag())
            .build(TokenSerializer.serialize(changePasswordToken, pass));

        mailSender.send(new MailMessage()
            .setRecipient(account.getEmail_address(), account.getDisplayName())
            .setLocale(locale)
            .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT_SUBJECT)
            .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT)
            .setHtml()
            .setData(ImmutableMap.of(
                RecoverMailSoyInfo.ForgotPasswordExistingAccountSoyTemplateInfo.RESET_PASSWORD_LINK, resetPasswordLink.toString()
            )));
      }
    } catch (MessagingException e) {
      logger.error("Error sending reset-password email", e);
      return form(Response.serverError(), locale, ForgotPasswordError.MESSAGING_ERROR);
    }

    // XXX: do not use the account's locale (if it exists) as that would be a hint that the account exists.
    return Response.ok()
        .entity(new SoyTemplate(RecoverSoyInfo.EMAIL_SENT, locale, ImmutableMap.of(
            RecoverSoyInfo.EmailSentSoyTemplateInfo.EMAIL_ADDRESS, email
        )))
        .build();
  }

  static Response form(Response.ResponseBuilder builder, ULocale locale, @Nullable ForgotPasswordError error) {
    ImmutableMap.Builder<String, String> localeUrlMap = ImmutableMap.builderWithExpectedSize(LocaleHelper.SUPPORTED_LOCALES.size());
    for (ULocale supportedLocale : LocaleHelper.SUPPORTED_LOCALES) {
      String languageTag = supportedLocale.toLanguageTag();
      URI uri = UriBuilder.fromResource(ForgotPasswordPage.class)
          .queryParam(LoginPage.LOCALE_PARAM, languageTag)
          .build();
      localeUrlMap.put(languageTag, uri.toString());
    }

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(3)
        .put(RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ForgotPasswordPage.class).build().toString())
        .put(RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.LOCALE_URL_MAP, localeUrlMap.build());
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
        .entity(new SoyTemplate(RecoverSoyInfo.FORGOT_PASSWORD, locale, data.build()))
        .build();
  }

  enum ForgotPasswordError {
    MISSING_REQUIRED_FIELD,
    MESSAGING_ERROR,
    EXPIRED_LINK
  }
}
