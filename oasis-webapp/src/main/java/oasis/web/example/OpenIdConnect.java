package oasis.web.example;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;

import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.HtmlEscapers;

import oasis.web.authn.Login;
import oasis.web.authn.Logout;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.TokenEndpoint;

@Path("/test-openidconnect")
public class OpenIdConnect {
  private static final String COOKIE_NAME = "TEST";
  private static final String STATE_COOKIE_NAME = "state";

  private static final DataStore CREDENTIALS_DATA_STORE;
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  static {
    try {
      CREDENTIALS_DATA_STORE = MemoryDataStoreFactory.getDefaultInstance().getDataStore("test");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private AuthorizationCodeFlow flow;

  @Context SecurityContext securityContext;
  private UriInfo uriInfo;

  @Context
  public void setUriInfo(UriInfo uriInfo) {
    this.uriInfo = uriInfo;

    // XXX: this should be a constant (static final), but we don't want to hard-code or parameterize URLs here (this demo runs from the same server as the IdP)
    flow = new AuthorizationCodeFlow.Builder(
            BearerToken.authorizationHeaderAccessMethod(),
            HTTP_TRANSPORT,
            JSON_FACTORY,
            new GenericUrl(uriInfo.getBaseUriBuilder().path(TokenEndpoint.class).build()), // This would be a fixed value IRL
            new BasicAuthentication("test", "password"),
            "test",
            uriInfo.getBaseUriBuilder().path(AuthorizationEndpoint.class).build().toString() // This would be a fixed value IRL
        )
        .setCredentialDataStore(CREDENTIALS_DATA_STORE)
        .setScopes(ImmutableSet.of("openid", "profile", "email"))
        .build();
  }

  @GET
  @Path("/index.html")
  @Produces(MediaType.TEXT_HTML)
  public Response protectedResource(@CookieParam(COOKIE_NAME) String sid) throws IOException {
    if (sid != null && !sid.isEmpty()) {
      Credential credential = flow.loadCredential(sid);
      // note: we can't use @Authenticated @User as we want to redirect to the OpenID Connect endpoint for authentication
      if (credential != null && credential.getAccessToken() != null) {
        return Response.ok(
            "<!doctype html>" +
                "<html>" +
                "<head>" +
                "<title>Welcome</title>" +
                "</head>" +
                "<body>" +
                "<h1>Hurray!</h1>" +
                // TODO: get user name from 'sid' once we get an IdToken
                "<p>You're authenticated with access token " + HtmlEscapers.htmlEscaper().escape(credential.getAccessToken()) +
                "<p><a href=" + HtmlEscapers.htmlEscaper().escape(UriBuilder.fromResource(OpenIdConnect.class).path(OpenIdConnect.class, "logout").build().toString()) + ">Logout</a>" +
                "</body>" +
                "</html>")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .header(HttpHeaders.CACHE_CONTROL, "private, must-revalidate")
            .build();
      }
      // No authentication stored for 'sid': fall through
    }
    String state = new BigInteger(130, new SecureRandom()).toString(32);
    AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl()
        .setState(state)
        .setRedirectUri(getRedirectUri());
    return Response.seeOther(authorizationUrl.toURI())
        .cookie(new NewCookie(STATE_COOKIE_NAME, state, null, null, null, NewCookie.DEFAULT_MAX_AGE, securityContext.isSecure(), true))
        .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
        .header(HttpHeaders.CACHE_CONTROL, "private, must-revalidate")
        .build();
  }

  private String getRedirectUri() {
    return uriInfo.getBaseUriBuilder().path(OpenIdConnect.class).path(OpenIdConnect.class, "callback").build().toASCIIString();
  }

  @GET
  @Path("/callback")
  public Response callback(@QueryParam("state") String state, @CookieParam(STATE_COOKIE_NAME) String cookieState) throws IOException {
    AuthorizationCodeResponseUrl responseUrl = new AuthorizationCodeResponseUrl(uriInfo.getRequestUri().toASCIIString());
    if (responseUrl.getError() != null) {
      return badRequest("Error: " + responseUrl.getError() + "(" + responseUrl.getErrorDescription() + ")");
    }
    String code = responseUrl.getCode();
    if (code == null) {
      return badRequest("Missing authorization code");
    }
    if (state == null || !state.equals(cookieState)) {
      return badRequest("Mismatching state (or missing state)");
    }
    IdTokenResponse response = IdTokenResponse.execute(flow.newTokenRequest(code).setRedirectUri(getRedirectUri()));
    IdToken token = response.parseIdToken();
    String userId = token.getPayload().getSubject();
    flow.createAndStoreCredential(response, userId);
    return Response.seeOther(UriBuilder.fromResource(OpenIdConnect.class).path(OpenIdConnect.class, "protectedResource").build())
        .cookie(new NewCookie(STATE_COOKIE_NAME, null, null, null, 0, null, 0, new Date(108, 0, 20, 11, 10), securityContext.isSecure(), true))
        .cookie(new NewCookie(COOKIE_NAME, userId, null, null, null, NewCookie.DEFAULT_MAX_AGE, securityContext.isSecure(), true))
        .build();
  }

  @GET
  @Path("/logout")
  @Produces(MediaType.TEXT_HTML)
  public Response logout() {
    return Response.ok()
        .type(MediaType.TEXT_HTML_TYPE)
        .cookie(new NewCookie(COOKIE_NAME, null, null, null, 0, null, 0, new Date(108, 0, 20, 11, 10), securityContext.isSecure(), true))
        .entity("<!doctype html>" +
            "<html>" +
            "<head>" +
            "<title>Logged out</title>" +
            "</head>" +
            "<body>" +
            "<h1>Logged out</h1>" +
            "<p>You've been logged out from the app, but not from OASIS." +
            "<p>If you go back to <a href=" + HtmlEscapers.htmlEscaper().escape(UriBuilder.fromResource(OpenIdConnect.class).path(OpenIdConnect.class, "protectedResource").build().toString()) + ">the app</a>," +
            "you'll be automatically signed-in again, unless you also logged out from OASIS." +
            "<p>You can log out from OASIS <a href=" + HtmlEscapers.htmlEscaper().escape(UriBuilder.fromResource(Logout.class).queryParam(Login.CONTINUE_PARAM, uriInfo.getBaseUriBuilder().path(OpenIdConnect.class).path(OpenIdConnect.class, "protectedResource").build()).build().toString()) + ">here</a>" +
            "</body>" +
            "</html>")
        .build();
  }

  private Response serverError(String message) {
    return Response.serverError()
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity(message)
        .build();
  }

  private Response badRequest(String message) {
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity(message)
        .build();
  }
}
