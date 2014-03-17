package oasis.web.authn;

import java.net.URI;

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

import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.model.accounts.Account;
import oasis.model.authn.SidToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.services.cookies.CookieFactory;
import oasis.web.StaticResources;
import oasis.web.security.StrictReferer;
import oasis.web.view.View;

@Path("/a/login")
public class LoginPage {
  private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

  public static final String CONTINUE_PARAM = "continue";

  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject TokenHandler tokenHandler;
  @Inject AuditLogService auditLogService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(@QueryParam(CONTINUE_PARAM) URI continueUrl) {
    return loginForm(Response.ok(), continueUrl, null);
  }

  @POST
  @StrictReferer
  public Response post(
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam("continue") URI continueUrl
  ) {
    if (userName.isEmpty()) {
      return loginForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, null);
    }
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    try {
      Account account = userPasswordAuthenticator.authenticate(userName, password);

      String pass = tokenHandler.generateRandom();
      SidToken sidToken = tokenHandler.createSidToken(account.getId(), pass);
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
    } catch (LoginException e) {
      log(userName, LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);

      return loginForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, "Incorrect username or password");
    }
  }

  private Response loginForm(Response.ResponseBuilder builder, URI continueUrl, String errorMessage) {
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    // Initialise un message d'erreur vide au besoin
    if ( errorMessage == null ) {
      errorMessage = "";
    }

    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new View(LoginPage.class, "Login.html", ImmutableMap.of(
            "formAction", UriBuilder.fromResource(LoginPage.class).build(),
            "continue", continueUrl,
            "errorMessage", errorMessage
        )))
        .build();
  }

  private URI defaultContinueUrl() {
    return defaultContinueUrl(uriInfo);
  }

  static URI defaultContinueUrl(UriInfo uriInfo) {
    return uriInfo.getBaseUriBuilder().path(StaticResources.class).path(StaticResources.class, "home").build();
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
      AUTHENTICATION_SUCCEEDED, AUTHENTICATION_FAILED
    }
  }
}
