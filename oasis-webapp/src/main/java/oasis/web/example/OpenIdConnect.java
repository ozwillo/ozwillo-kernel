package oasis.web.example;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.HtmlEscapers;
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
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import oasis.web.authn.Login;
import oasis.web.authn.Logout;

@Path("/test-openidconnect")
public class OpenIdConnect {

  private static final String COOKIE_NAME = "TEST";
  private static final String STATE_COOKIE_NAME = "state";

  private static final DataStore CREDENTIALS_DATA_STORE;

  static {
    try {
      CREDENTIALS_DATA_STORE = MemoryDataStoreFactory.getDefaultInstance().getDataStore("test");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static AuthorizationCodeFlow.Builder FLOW_BUILDER = new AuthorizationCodeFlow.Builder(
          BearerToken.authorizationHeaderAccessMethod(),
          new NetHttpTransport(),
          new JacksonFactory(),
          new GenericUrl("http://localhost:8080/a/token"),
          new BasicAuthentication("test", "password"),
          "test",
          "http://localhost:8080/a/auth"
      )
      .setCredentialDataStore(CREDENTIALS_DATA_STORE)
      .setScopes(ImmutableSet.of("openid", "profile", "email"));

  @Context UriInfo uriInfo;

  @GET
  @Path("/index.html")
  @Produces(MediaType.TEXT_HTML)
  public Response protectedResource(@CookieParam(COOKIE_NAME) String sid) throws IOException {
    AuthorizationCodeFlow flow = FLOW_BUILDER.build();
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
        .cookie(new NewCookie(STATE_COOKIE_NAME, state, null, null, null, NewCookie.DEFAULT_MAX_AGE, false, true))
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
      return Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("Error: " + responseUrl.getError() + "(" + responseUrl.getErrorDescription() + ")")
          .build();
    }
    String code = responseUrl.getCode();
    if (code == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("Missing authorization code")
          .build();
    }
    if (state == null || !state.equals(cookieState)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("Mismatching state (or missing state)")
          .build();
    }
    AuthorizationCodeFlow flow = FLOW_BUILDER.build();
    // TODO: use IdTokenResponse.execute() once we send an IdToken
//    IdTokenResponse response = IdTokenResponse.execute(flow.newTokenRequest(code).setRedirectUri(getRedirectUri()));
//    IdToken token = response.parseIdToken();
//    String userId = token.getPayload().getSubject();
    TokenResponse response = flow.newTokenRequest(code).setRedirectUri(getRedirectUri()).execute();
    String userId = response.getAccessToken();
    Credential credential = flow.createAndStoreCredential(response, userId);
    return Response.seeOther(UriBuilder.fromResource(OpenIdConnect.class).path(OpenIdConnect.class, "protectedResource").build())
        .cookie(new NewCookie(STATE_COOKIE_NAME, null, null, null, 0, null, 0, new Date(108, 0, 20, 11, 10), false, true))
        .cookie(new NewCookie(COOKIE_NAME, userId, null, null, null, NewCookie.DEFAULT_MAX_AGE, false, true))
        .build();
  }

  @GET
  @Path("/logout")
  @Produces(MediaType.TEXT_HTML)
  public Response logout() {
    return Response.ok()
        .type(MediaType.TEXT_HTML_TYPE)
        .cookie(new NewCookie(COOKIE_NAME, null, null, null, 0, null, 0, new Date(108, 0, 20, 11, 10), false, true))
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
}