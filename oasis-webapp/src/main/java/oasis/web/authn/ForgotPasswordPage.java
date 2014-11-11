package oasis.web.authn;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
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
import oasis.soy.templates.ChangePasswordSoyInfo;
import oasis.soy.templates.RecoverMailSoyInfo;
import oasis.soy.templates.RecoverSoyInfo;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/a/recover")
public class ForgotPasswordPage {
  private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordPage.class);

  @Inject AccountRepository accountRepository;
  @Inject MailModule.Settings mailSettings;
  @Inject MailSender mailSender;
  @Inject TokenHandler tokenHandler;

  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get() {
    if (!mailSettings.enabled) {
      return ResponseFactory.NOT_FOUND;
    }
    return form(Response.ok(), null);
  }

  @POST
  public Response post(
      @FormParam("u") String email
  ) {
    if (!mailSettings.enabled) {
      return ResponseFactory.NOT_FOUND;
    }

    if (Strings.isNullOrEmpty(email)) {
      return form(Response.status(Response.Status.BAD_REQUEST), ForgotPasswordError.MISSING_REQUIRED_FIELD);
    }

    // TODO: i18n
    Locale locale = Locale.ROOT;

    UserAccount account = accountRepository.getUserAccountByEmail(email);
    try {
      if (account == null) {
        mailSender.send(new MailMessage()
          .setRecipient(email, null)
          .setLocale(locale)
          .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT_SUBJECT)
          .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_UNKNOWN_ACCOUNT)
          .setPlainText());
      } else {
        String pass = tokenHandler.generateRandom();
        ChangePasswordToken changePasswordToken = tokenHandler.createChangePasswordToken(account.getId(), pass);
        URI resetPasswordLink = Resteasy1099.getBaseUriBuilder(uriInfo).path(ResetPasswordPage.class).build(TokenSerializer.serialize(changePasswordToken, pass));

        mailSender.send(new MailMessage()
            .setRecipient(account.getEmail_address(), account.getDisplayName())
            .setLocale(account.getLocale())
            .setSubject(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT_SUBJECT)
            .setBody(RecoverMailSoyInfo.FORGOT_PASSWORD_EXISTING_ACCOUNT)
            .setPlainText()
            .setData(new SoyMapData(
                RecoverMailSoyInfo.ForgotPasswordExistingAccountSoyTemplateInfo.RESET_PASSWORD_LINK, resetPasswordLink.toString()
            )));
      }
    } catch (MessagingException e) {
      logger.error("Error sending reset-password email", e);
      return form(Response.serverError(), ForgotPasswordError.MESSAGING_ERROR);
    }

    // XXX: do not use the account's locale (if it exists) as that would be a hint that the account exists.
    return Response.ok()
        .entity(new SoyTemplate(RecoverSoyInfo.EMAIL_SENT, locale, new SoyMapData(
            RecoverSoyInfo.EmailSentSoyTemplateInfo.EMAIL_ADDRESS, email
        )))
        .build();
  }

  static Response form(Response.ResponseBuilder builder, @Nullable ForgotPasswordError error) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(RecoverSoyInfo.FORGOT_PASSWORD,
            // TODO: i18n
            Locale.ROOT,
            new SoyMapData(
                RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ForgotPasswordPage.class).build().toString(),
                RecoverSoyInfo.ForgotPasswordSoyTemplateInfo.ERROR, error == null ? null : error.name()
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
