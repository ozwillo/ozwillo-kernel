package oasis.web.authn;

import java.net.URI;
import java.util.Locale;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.template.soy.data.SoyMapData;

import oasis.auditlog.AuditLogService;
import oasis.mail.MailMessage;
import oasis.mail.MailModule;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.auth.AuthModule;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LoginSoyInfo;
import oasis.soy.templates.SignUpSoyInfo;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.security.StrictReferer;
import oasis.web.utils.UserAgentFingerprinter;

@Path("/a/signup")
public class SignUpPage {
  private static Logger logger = LoggerFactory.getLogger(SignUpPage.class);

  public static final String CONTINUE_PARAM = "continue";

  @Inject AccountRepository accountRepository;
  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject CredentialsRepository credentialsRepository;
  @Inject TokenHandler tokenHandler;
  @Inject UserAgentFingerprinter fingerprinter;
  @Inject AuditLogService auditLogService;
  @Inject AuthModule.Settings settings;
  @Inject MailModule.Settings mailSettings;
  @Inject @Nullable MailSender mailSender;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response signUp(
      @Context HttpHeaders headers,
      @QueryParam(CONTINUE_PARAM) URI continueUrl,
      @FormParam("email") String email,
      @FormParam("pwd") String password,
      @FormParam("nickname") String nickname
  ) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(settings.landingPage, uriInfo);
    }

    if (Strings.isNullOrEmpty(email) || Strings.isNullOrEmpty(password) || Strings.isNullOrEmpty(nickname)) {
      return LoginPage.signupForm(Response.ok(), continueUrl, settings, LoginPage.SignupError.MISSING_REQUIRED_FIELD);
    }
    // TODO: Verify that the password as a sufficiently strong length or even a strong entropy

    UserAccount account = new UserAccount();
    account.setEmail_address(email);
    // XXX: Auto-verify the email address when mail is disabled (otherwise the user couldn't sign in)
    if (!mailSettings.enabled) {
      account.setEmail_verified(true);
    }
    account.setNickname(nickname);
    // TODO: Use the user-selected locale
    account.setLocale(Locale.UK);
    // TODO: Use a zoneinfo "matching" the selected locale
    account.setZoneinfo("Europe/Paris");
    account = accountRepository.createUserAccount(account);
    if (account == null) {
      // TODO: Allow the user to retrieve their password
      return LoginPage.signupForm(Response.ok(), continueUrl, settings, LoginPage.SignupError.ACCOUNT_ALREADY_EXISTS);
    } else {
      userPasswordAuthenticator.setPassword(account.getId(), password);
    }

    if (mailSettings.enabled) {
      // TODO: send email asynchronously
      try {
        // FIXME: use a true token (with expiry, etc.) rather than just the account ID
        URI activationLink = Resteasy1099.getBaseUriBuilder(uriInfo).path(ActivateAccountPage.class).build(account.getId());
        mailSender.send(new MailMessage()
            .setRecipient(email, nickname)
            .setLocale(account.getLocale())
            .setSubject(SignUpSoyInfo.ACTIVATE_ACCOUNT_SUBJECT)
            .setBody(SignUpSoyInfo.ACTIVATE_ACCOUNT)
            .setPlainText()
            .setData(new SoyMapData(
                SignUpSoyInfo.ActivateAccountSoyTemplateInfo.NICKNAME, nickname,
                SignUpSoyInfo.ActivateAccountSoyTemplateInfo.ACTIVATION_LINK, activationLink.toString()
            )));
        // TODO: redirect to a bookmarkable URI (with form to resend the activation mail)
        return Response.ok()
            .entity(new SoyTemplate(LoginSoyInfo.ACCOUNT_PENDING_ACTIVATION, account.getLocale()))
            .build();
      } catch (MessagingException e) {
        logger.error("Error sending activation email", e);
        accountRepository.deleteUserAccount(account.getId());
        credentialsRepository.deleteCredentials(ClientType.USER, account.getId());
        return LoginPage.signupForm(Response.ok(), continueUrl, settings, LoginPage.SignupError.MESSAGING_ERROR);
      }
    } else {
      byte[] fingerprint = fingerprinter.fingerprint(headers);
      // XXX: automatically sign the user in when mail is disabled
      return LoginPage.authenticate(email, account, continueUrl, fingerprint, tokenHandler, auditLogService, securityContext);
    }
  }
}
