package oasis.web.authn;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SoyMapData;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.soy.templates.SignUpSoyInfo;
import oasis.web.utils.ResponseFactory;

@Path("/a/activate/{token}")
@Produces(MediaType.TEXT_HTML)
public class ActivateAccountPage {
  private static Logger logger = LoggerFactory.getLogger(ActivateAccountPage.class);

  @Inject AccountRepository accountRepository;
  @Inject OpenIdConnectModule.Settings settings;
  @Inject MailSender mailSender;

  @Context UriInfo uriInfo;

  @PathParam("token") String token;

  @GET
  public Response activate() {
    // FIXME: use a true token (with expiry, etc.) rather than just the account ID
    UserAccount userAccount = accountRepository.verifyEmailAddress(token);
    if (userAccount == null) {
      // XXX: return error message rather than blank page
      return ResponseFactory.NOT_FOUND;
    }
    try {
      mailSender.send(new MailMessage()
          .setRecipient(userAccount.getEmail_address(), userAccount.getDisplayName())
          .setSubject(SignUpSoyInfo.ACCOUNT_ACTIVATED_SUBJECT)
          .setBody(SignUpSoyInfo.ACCOUNT_ACTIVATED)
          .setPlainText()
          .setData(new SoyMapData(
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.NICKNAME, userAccount.getDisplayName()
          )));
    } catch (MessagingException e) {
      logger.error("Error sending welcome email", e);
      // fall through: it's unfortunate but not critical.
    }
    return Response.seeOther(LoginPage.defaultContinueUrl(settings.landingPage, uriInfo)).build();
  }
}
