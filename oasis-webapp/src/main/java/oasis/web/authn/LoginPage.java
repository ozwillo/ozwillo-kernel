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

import com.google.common.collect.ImmutableMap;

import oasis.model.accounts.Account;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.services.cookies.CookieFactory;
import oasis.web.StaticResources;
import oasis.web.security.StrictReferer;
import oasis.web.view.View;

@Path("/a/login")
public class LoginPage {
  public static final String CONTINUE_PARAM = "continue";

  @Inject
  UserPasswordAuthenticator userPasswordAuthenticator;

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

    // Attempt auth
    try {
      // Attempt auth
      Account account = userPasswordAuthenticator.authenticate(userName, password);

      // TODO: generate session ID
      // TODO: One-Time Password
      return Response
          .seeOther(continueUrl)
          .cookie(CookieFactory.createSessionCookie(UserAuthenticationFilter.COOKIE_NAME, account.getId(), securityContext.isSecure())) // TODO: remember me
          .build();
    } catch (LoginException e) {
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
}