package oasis.web.authn;

import java.net.URI;

import javax.inject.Inject;
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

import com.google.api.client.repackaged.com.google.common.base.Strings;

import oasis.auditlog.AuditLogService;
import oasis.model.accounts.UserAccount;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.SignUpService;
import oasis.services.authn.TokenHandler;
import oasis.web.security.StrictReferer;
import oasis.web.utils.UserAgentFingerprinter;

@Path("/a/signup")
public class SignUpPage {
  public static final String CONTINUE_PARAM = "continue";

  @Inject SignUpService signUpService;
  @Inject TokenHandler tokenHandler;
  @Inject UserAgentFingerprinter fingerprinter;
  @Inject AuditLogService auditLogService;
  @Inject OpenIdConnectModule.Settings settings;

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
      return LoginPage.loginForm(Response.ok(), continueUrl, settings, "Some required fields are not filled");
    }
    // TODO: Verify that the password as a sufficiently strong length or even a strong entropy

    // TODO: Send an activation email to verify the existence of the email address
    UserAccount account = signUpService.signUp(email, password, nickname);
    if (account == null) {
      // TODO: Allow the user to retrieve their password
      return LoginPage.loginForm(Response.ok(), continueUrl, settings, "The username already exists.");
    }

    byte[] fingerprint = fingerprinter.fingerprint(headers);

    // XXX: As the activation email feature is not already made, automatically sign the user in
    return LoginPage.authenticate(email, account, continueUrl, fingerprint, tokenHandler, auditLogService, securityContext);
  }
}
