package oasis.web.authn;

import java.net.URI;

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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.services.cookies.CookieFactory;
import oasis.web.StaticResources;
import oasis.web.security.StrictReferer;
import oasis.web.utils.UserAgentFingerprinter;
import oasis.web.view.View;

@User
@Path("/a/login")
public class LoginPage {
  private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

  public static final String CONTINUE_PARAM = "continue";
  public static final String CANCEL_PARAM = "cancel";

  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AccountRepository accountRepository;
  @Inject AuditLogService auditLogService;
  @Inject UserAgentFingerprinter fingerprinter;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @QueryParam(CONTINUE_PARAM) URI continueUrl,
      @QueryParam(CANCEL_PARAM) URI cancelUrl
  ) {
    return loginForm(Response.ok(), continueUrl, cancelUrl, null);
  }

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response post(
      @Context HttpHeaders headers,
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam("continue") URI continueUrl,
      @FormParam("cancel") URI cancelUrl
  ) {
    if (userName.isEmpty()) {
      return loginForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, cancelUrl, null);
    }
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    Account account;
    try {
      account = userPasswordAuthenticator.authenticate(userName, password);
    } catch (LoginException e) {
      log(userName, LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);
      return loginForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, cancelUrl, "Incorrect username or password");
    }

    if (securityContext.getUserPrincipal() != null) {
      return reAuthenticate(userName, account, continueUrl, cancelUrl);
    }

    byte[] fingerprint = fingerprinter.fingerprint(headers);
    return authenticate(userName, account, continueUrl, fingerprint);
  }

  private Response authenticate(String userName, Account account, URI continueUrl, byte[] fingerprint) {
    String pass = tokenHandler.generateRandom();
    SidToken sidToken = tokenHandler.createSidToken(account.getId(), fingerprint, pass);
    if (sidToken == null) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("No SidToken was created for Account {}.", account.getId());
      return Response.serverError().build();
    }

    log(userName, LoginLogEvent.LoginResult.AUTHENTICATION_SUCCEEDED);

    // TODO: One-Time Password
    return Response
        .seeOther(continueUrl)
        .cookie(CookieFactory.createSessionCookie(UserFilter.COOKIE_NAME, TokenSerializer.serialize(sidToken, pass), securityContext.isSecure())) // TODO: remember me
        .build();
  }

  private Response reAuthenticate(String userName, Account account, URI continueUrl, URI cancelUrl) {SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    if (!account.getId().equals(sidToken.getAccountId())) {
      // Form has been tampered with, or user signed in with another account since the form was generated
      // Re-display the form: if user signed out/in, it will show the new (current) user.
      return loginForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, cancelUrl, null);
    }
    if (!tokenRepository.reAuthSidToken(sidToken.getId())) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("Error when updating SidToken {} for Account {}.", sidToken.getId(), account.getId());
      return Response.serverError().build();
    }

    log(userName, LoginLogEvent.LoginResult.RE_ENTER_PASSWORD_SUCCEEDED);

    return Response.seeOther(continueUrl).build();
  }

  private Response loginForm(Response.ResponseBuilder builder, @Nullable URI continueUrl, @Nullable URI cancelUrl, @Nullable String errorMessage) {
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    String reauthEmail;
    if (securityContext.getUserPrincipal() != null) {
      SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
      UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());
      // XXX: what if account is null?
      reauthEmail = account.getEmailAddress();
    } else {
      reauthEmail = "";
    }

    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new View(LoginPage.class, "Login.html", ImmutableMap.of(
            "reauthEmail", reauthEmail,
            "formAction", UriBuilder.fromResource(LoginPage.class).build(),
            "continue", continueUrl,
            "cancel", Objects.firstNonNull(cancelUrl, ""),
            "errorMessage", Strings.nullToEmpty(errorMessage)
        )))
        .build();
  }

  private URI defaultContinueUrl() {
    return defaultContinueUrl(uriInfo);
  }

  static URI defaultContinueUrl(UriInfo uriInfo) {
    return defaultContinueUrl(uriInfo.getBaseUriBuilder());
  }

  @VisibleForTesting static URI defaultContinueUrl(UriBuilder baseUriBuilder) {
    return baseUriBuilder.path(StaticResources.class).path(StaticResources.class, "home").build();
  }

  private void log(String userName, LoginLogEvent.LoginResult loginResult) {
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
}
