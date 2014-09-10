package oasis.web.authz;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Clock;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.resteasy.Resteasy1099;

@Path("/a/token")
@Authenticated @Client
@Api(value = "/a/token", description = "Token Endpoint.")
public class TokenEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(TokenEndpoint.class);
  private static final Joiner SCOPE_JOINER = Joiner.on(' ').skipNulls();
  private static final Splitter SCOPE_SPLITTER = Splitter.on(' ');
  private static final JsonWebSignature.Header JWS_HEADER = new JsonWebSignature.Header()
      .setType("JWS")
      .setAlgorithm("RS256")
      .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID);

  @Inject OpenIdConnectModule.Settings settings;
  @Inject JsonFactory jsonFactory;
  @Inject Clock clock;

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  private MultivaluedMap<String, String> params;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Exchange an authorization code or a refresh token for an access token.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.2\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#ObtainingTokens\">OpenID Connect RFC</a> for more information."
  )
  public Response validate(MultivaluedMap<String, String> params) throws GeneralSecurityException, IOException {
    this.params = params;

    String grant_type = getRequiredParameter("grant_type");

    // TODO: support other kind of tokens (jwt-bearer?)
    switch(grant_type) {
      case "authorization_code":
        return this.validateAuthorizationCode();

      case "refresh_token":
        return this.validateRefreshToken();

      default:
        return errorResponse("unsupported_grant_type", null);
    }
  }

  private Response validateRefreshToken() throws GeneralSecurityException, IOException {
    String refresh_token = getRequiredParameter("refresh_token");

    String asked_scopes_param = getParameter("scope");

    // Get the token behind the given code
    RefreshToken refreshToken = tokenHandler.getCheckedToken(refresh_token, RefreshToken.class);

    if (refreshToken == null) {
      return errorResponse("invalid_token", null);
    }

    // Verify that the client that wants to use the refresh token is the client that created it
    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    if (!refreshToken.getServiceProviderId().equals(client_id)) {
      logger.error("The serviceProvider {} wanted to access a token which belongs to the serviceProvider {}.", client_id, refreshToken.getServiceProviderId());
      // Not a Forbidden status because it could give the information that the refresh token really exists
      return errorResponse("invalid_token", null);
    }

    Set<String> asked_scopes;
    if (!Strings.isNullOrEmpty(asked_scopes_param)) {
      asked_scopes = Sets.newHashSet(SCOPE_SPLITTER.splitToList(asked_scopes_param));

      if (!refreshToken.getScopeIds().containsAll(asked_scopes)) {
        return errorResponse("invalid_scope", null);
      }
    } else {
      asked_scopes = refreshToken.getScopeIds();
    }

    String pass = tokenHandler.generateRandom();
    AccessToken accessToken = tokenHandler.createAccessToken(refreshToken, asked_scopes, pass);

    if (accessToken == null) {
      return Response.serverError().build();
    }

    String access_token = TokenSerializer.serialize(accessToken, pass);

    TokenResponse response = new TokenResponse();
    response.setAccessToken(access_token);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(accessToken.expiresIn().getStandardSeconds());
    response.setScope(SCOPE_JOINER.join(asked_scopes));

    return response(Response.Status.OK, response);
  }

  private Response validateAuthorizationCode() throws GeneralSecurityException, IOException {
    String auth_code = getRequiredParameter("code");

    String redirect_uri = getRequiredParameter("redirect_uri");

    // Get the token behind the given code
    AuthorizationCode authorizationCode = tokenHandler.getCheckedToken(auth_code, AuthorizationCode.class);

    if (authorizationCode == null) {
      return errorResponse("invalid_grant", null);
    }

    if (!authorizationCode.getRedirectUri().equals(redirect_uri)) {
      logger.error("Received redirect_uri {} does not match the one received at the authorization request.", redirect_uri);
      return errorResponse("invalid_grant", "Mismatching redirect_uri");
    }

    // Verify that the client that wants to use the authorization code is the client that created it
    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    if (!authorizationCode.getServiceProviderId().equals(client_id)) {
      logger.error("The serviceProvider {} wanted to access a token which belongs to the serviceProvider {}.", client_id, authorizationCode.getServiceProviderId());
      // Not a Forbidden status because it could give the information that the authorization code really exists
      return errorResponse("invalid_grant", null);
    }

    IdTokenResponse response = new IdTokenResponse();

    AccessToken accessToken;
    final String pass = tokenHandler.generateRandom();
    if (authorizationCode.getScopeIds().contains("offline_access")) {
      String refreshPass = tokenHandler.generateRandom();
      RefreshToken refreshToken = tokenHandler.createRefreshToken(authorizationCode, refreshPass);

      if (refreshToken == null) {
        return Response.serverError().build();
      }
      String refresh_token = TokenSerializer.serialize(refreshToken, refreshPass);

      response.setRefreshToken(refresh_token);

      accessToken = tokenHandler.createAccessToken(refreshToken, refreshToken.getScopeIds(), pass);
    } else {
      accessToken = tokenHandler.createAccessToken(authorizationCode, pass);
    }

    if (accessToken == null) {
      return Response.serverError().build();
    }

    String access_token = TokenSerializer.serialize(accessToken, pass);

    long issuedAt = TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());
    // Unconditionally send auth_time, as per https://tools.ietf.org/html/draft-hunt-oauth-v2-user-a4c
    Long authTime = null;
    Token token = tokenRepository.getToken(Iterables.getLast(authorizationCode.getAncestorIds()));
    if (token instanceof SidToken) {
      authTime = TimeUnit.MILLISECONDS.toSeconds(((SidToken) token).getAuthenticationTime().getMillis());
    } // TODO: else, log error/warning

    // Compute whether the user is a "user of the app" and/or "admin of the app"
    AppInstance appInstance = appInstanceRepository.getAppInstance(accessToken.getServiceProviderId());
    boolean isAppUser = accessControlRepository.getAccessControlEntry(appInstance.getId(), accessToken.getAccountId()) != null;
    boolean isAppAdmin = appAdminHelper.isAdmin(accessToken.getAccountId(), appInstance);

    response.setAccessToken(access_token);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(accessToken.expiresIn().getStandardSeconds());
    response.setScope(SCOPE_JOINER.join(accessToken.getScopeIds()));
    response.setIdToken(JsonWebSignature.signUsingRsaSha256(
        settings.keyPair.getPrivate(),
        jsonFactory,
        JWS_HEADER,
        new IdToken.Payload()
            .setIssuer(Resteasy1099.getBaseUri(uriInfo).toString())
            .setSubject(accessToken.getAccountId())
            .setAudience(client_id)
            .setExpirationTimeSeconds(issuedAt + settings.idTokenDuration.getStandardSeconds())
            .setIssuedAtTimeSeconds(issuedAt)
            .setNonce(authorizationCode.getNonce())
            .setAuthorizationTimeSeconds(authTime)
            .set("app_user", isAppUser ? Boolean.TRUE : null)
            .set("app_admin", isAppAdmin ? Boolean.TRUE : null)
    ));

    return response(Response.Status.OK, response);
  }

  private Response response(Response.Status status, Object response) {
    return Response.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("Pragma", "no-cache")
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(response)
        .build();
  }

  @Nullable
  private String getParameter(String paramName) {
    if (params == null) { // Workaround for https://issues.jboss.org/browse/RESTEASY-1004
      return null;
    }
    List<String> values = params.get(paramName);
    if (values == null || values.isEmpty()) {
      return null;
    }
    if (values.size() != 1) {
      throw tooManyValues(paramName);
    }
    String value = values.get(0);
    if (value != null) {
      value = value.trim();
      if (value.isEmpty()) {
        value = null;
      }
    }
    return value;
  }

  @Nonnull
  private String getRequiredParameter(String paramName) {
    String value = getParameter(paramName);
    if (value == null) {
      throw missingRequiredParameter(paramName);
    }
    return value;
  }

  private WebApplicationException missingRequiredParameter(String paramName) {
    return invalidRequest("Missing required parameter: " + paramName);
  }

  private WebApplicationException tooManyValues(String paramName) {
    return invalidRequest(paramName + " included more than once");
  }

  private WebApplicationException invalidRequest(String message) {
    return error("invalid_request", message);
  }

  private WebApplicationException error(String error, @Nullable String description) {
    return new BadRequestException(errorResponse(error, description));
  }

  private Response errorResponse(String error, @Nullable String description) {
    TokenErrorResponse response = new TokenErrorResponse()
        .setError(error)
        .setErrorDescription(description);
    return response(Response.Status.BAD_REQUEST, response);
  }
}
