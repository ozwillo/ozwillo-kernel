package oasis.web.authn;

import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccountActivationToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.soy.templates.SignUpSoyInfo;
import oasis.urls.Urls;
import oasis.web.utils.ResponseFactory;

@Path("/a/activate/{token}")
@Produces(MediaType.TEXT_HTML)
public class ActivateAccountPage {
  private static Logger logger = LoggerFactory.getLogger(ActivateAccountPage.class);

  @Inject AccountRepository accountRepository;
  @Inject TokenHandler tokenHandler;
  @Inject TokenRepository tokenRepository;
  @Inject Urls urls;
  @Inject MailSender mailSender;

  @Context UriInfo uriInfo;

  @PathParam("token") String token;
  @QueryParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale;

  @GET
  public Response activate() {
    AccountActivationToken accountActivationToken = tokenHandler.getCheckedToken(token, AccountActivationToken.class);
    if (accountActivationToken == null) {
      // XXX: return error message rather than blank page
      return ResponseFactory.NOT_FOUND;
    }
    UserAccount userAccount = accountRepository.verifyEmailAddress(accountActivationToken.getAccountId());
    if (userAccount == null) {
      // XXX: return error message rather than blank page
      return ResponseFactory.NOT_FOUND;
    }

    tokenRepository.revokeTokensForAccount(accountActivationToken.getAccountId());

    URI portalUrl = LoginPage.defaultContinueUrl(urls.myOasis(), uriInfo);
    try {
      mailSender.send(new MailMessage()
          .setRecipient(userAccount.getEmail_address(), userAccount.getDisplayName())
          .setLocale(userAccount.getLocale())
          .setSubject(SignUpSoyInfo.ACCOUNT_ACTIVATED_SUBJECT)
          .setBody(SignUpSoyInfo.ACCOUNT_ACTIVATED)
          .setHtml()
          .setData(new SoyMapData(
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.NICKNAME, userAccount.getDisplayName(),
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.PORTAL_URL, portalUrl.toString()
          )));
    } catch (MessagingException e) {
      logger.error("Error sending welcome email", e);
      // fall through: it's unfortunate but not critical.
    }
    return Response.seeOther(portalUrl).build();
  }
}
