package oasis.web.authn;

import java.net.URI;
import java.util.Locale;

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
import com.google.template.soy.data.SoyMapData;

import oasis.mail.MailMessage;
import oasis.mail.MailModule;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ChangePasswordToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.RecoverMailSoyInfo;
import oasis.soy.templates.RecoverSoyInfo;
import oasis.web.i18n.LocaleHelper;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/a/recover")
public class ForgotPasswordPage {
  private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordPage.class);

  @Inject AccountRepository accountRepository;
  @Inject MailModule.Settings mailSettings;
  @Inject MailSender mailSender;
  @Inject TokenHandler tokenHandler;
  @Inject LocaleHelper localeHelper;

  @Context UriInfo uriInfo;
  @Context Request request;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @QueryParam(LoginPage.LOCALE_PARAM) @Nullable Locale locale
  ) {
    if (!mailSettings.enabled) {
      return ResponseFactory.NOT_FOUND;
    }
    return form(Response.ok(), localeHelper.selectLocale(locale, request), null);
  }

  @POST
  public Response post(
      @FormParam(LoginPage.LOCALE_PARAM) @Nullable Locale locale,
      @FormParam("u") String email
  ) {
    if (!mailSettings.enabled) {
      return ResponseFactory.NOT_FOUND;
    }

    locale = localeHelper.selectLocale(locale, request);

    if (Strings.isNullOrEmpty(email)) {
      return form(Response.status(Response.Status.BAD_REQUEST), locale, ForgotPasswordError.MISSING_REQUIRED_FIELD);
    }

    UserAccount account = accountRepository.getUserAccountByEmail(email);
    try {
      if (account == null) {
        mailSender.send(new MailMessage()
          .setRecipient(email, null)
          .setLocale(locale)
          .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT_SUBJECT)
          .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT)
          .setHtml());
      } else {
        String pass = tokenHandler.generateRandom();
        ChangePasswordToken changePasswordToken = tokenHandler.createChangePasswordToken(account.getId(), pass);
        URI resetPasswordLink = Resteasy1099.getBaseUriBuilder(uriInfo)
            .path(ResetPasswordPage.class)
            .queryParam(LoginPage.LOCALE_PARAM, locale.toLanguageTag())
            .build(TokenSerializer.serialize(changePasswordToken, pass));

        mailSender.send(new MailMessage()
            .setRecipient(account.getEmail_address(), account.getDisplayName())
            .setLocale(locale)
            .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT_SUBJECT)
            .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT)
            .setHtml()
            .setData(new SoyMapData(
                RecoverMailSoyInfo.ForgotPasswordExistingAccountSoyTemplateInfo.RESET_PASSWORD_LINK, resetPasswordLink.toString()
            )));
      }
    } catch (MessagingException e) {
      logger.error("Error sending reset-password email", e);
      return form(Response.serverError(), locale, ForgotPasswordError.MESSAGING_ERROR);
    }

    // XXX: do not use the account's locale (if it exists) as that would be a hint that the account exists.
    return Response.ok()
        .entity(new SoyTemplate(RecoverSoyInfo.EMAIL_SENT, locale, new SoyMapData(
            RecoverSoyInfo.EmailSentSoyTemplateInfo.EMAIL_ADDRESS, email
        )))
        .build();
  }

  static Response form(Response.ResponseBuilder builder, Locale locale, @Nullable ForgotPasswordError error) {
    SoyMapData localeUrlMap = new SoyMapData();
    for (Locale supportedLocale : LocaleHelper.SUPPORTED_LOCALES) {
      String languageTag = supportedLocale.toLanguageTag();
      URI uri = UriBuilder.fromResource(ForgotPasswordPage.class)
          .queryParam(LoginPage.LOCALE_PARAM, languageTag)
          .build();
      localeUrlMap.put(languageTag, uri.toString());
    }
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(RecoverSoyInfo.FORGOT_PASSWORD,
            locale,
            new SoyMapData(
                RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ForgotPasswordPage.class).build().toString(),
                RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.ERROR, error == null ? null : error.name(),
                RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.LOCALE_URL_MAP, localeUrlMap
            )
        ))
        .build();
  }

  enum ForgotPasswordError {
    MISSING_REQUIRED_FIELD,
    MESSAGING_ERROR,
    EXPIRED_LINK
  }
}
