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
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.AuthorizationCode;
import oasis.model.accounts.RefreshToken;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Path("/a/token")
@Authenticated @Client
@Api(value = "/a/token", description = "Token Endpoint.")
public class TokenEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(TokenEndpoint.class);
  private static final Joiner SCOPE_JOINER = Joiner.on(' ').skipNulls();
  private static final Splitter SCOPE_SPLITTER = Splitter.on(' ');
  private static final JsonWebSignature.Header JWS_HEADER = new JsonWebSignature.Header().setType("JWS").setAlgorithm("RS256");

  @Inject OpenIdConnectModule.Settings settings;
  @Inject JsonFactory jsonFactory;
  @Inject Clock clock;

  @Inject AccountRepository accountRepository;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;

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
        return this.validateAccessCode(params);

      case "refresh_token":
        return this.validateRefreshToken(params);

      default:
        return errorResponse("unsupported_grant_type", null);
    }
  }

  // TODO : Move this method in TokenHandler
  private Token getCheckedToken(Token token) {
    if (token == null) {
      return null;
    }

    // If someone fakes a token, at least it should ensure it hasn't expired
    // It saves us a database lookup.
    if (!tokenHandler.checkTokenValidity(token)) {
      return null;
    }

    Token realToken = tokenRepository.getToken(token.getId());
    if (realToken == null || !tokenHandler.checkTokenValidity(realToken)) {
      // token does not exist or has expired
      return null;
    }

    return realToken;
  }

  private Response validateRefreshToken(MultivaluedMap<String, String> params) throws GeneralSecurityException, IOException {
    String refresh_token = getRequiredParameter("refresh_token");

    // Get the token behind the given code
    Token token = getCheckedToken(TokenSerializer.unserialize(refresh_token));

    if (token == null) {
      return errorResponse("invalid_token", null);
    }

    Account account = accountRepository.getAccountByTokenId(token.getId());

    if (account == null  || !tokenHandler.checkTokenValidity(token) || !(token instanceof RefreshToken)) {
      return errorResponse("invalid_token", null);
    }

    RefreshToken refreshToken = (RefreshToken)token;

    String asked_scopes_param = getParameter("scope");
    Set<String> asked_scopes;
    if (!Strings.isNullOrEmpty(asked_scopes_param)) {
      asked_scopes = Sets.newHashSet(SCOPE_SPLITTER.splitToList(asked_scopes_param));
    } else {
      asked_scopes = refreshToken.getScopeIds();
    }

    if (!refreshToken.getScopeIds().containsAll(asked_scopes)) {
      return errorResponse("invalid_scope", null);
    }

    AccessToken accessToken = tokenHandler.createAccessToken(account.getId(), refreshToken, asked_scopes);

    if (accessToken == null) {
      return Response.serverError().build();
    }

    String access_token = TokenSerializer.serialize(accessToken);

    TokenResponse response = new TokenResponse();
    response.setAccessToken(access_token);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(accessToken.getTimeToLive().getStandardSeconds());
    response.setScope(SCOPE_JOINER.join(asked_scopes));

    return response(Response.Status.OK, response);
  }

  public Response validateAccessCode(MultivaluedMap<String, String> params) throws GeneralSecurityException, IOException {
    String auth_code = getRequiredParameter("code");

    // TODO: Validate redirect_uri
    String redirect_uri = getRequiredParameter("redirect_uri");

    // Get the token behind the given code
    Token token = getCheckedToken(TokenSerializer.unserialize(auth_code));

    if (token == null) {
      return errorResponse("invalid_token", null);
    }

    Account account = accountRepository.getAccountByTokenId(token.getId());

    if (account == null  || !tokenHandler.checkTokenValidity(token) || !(token instanceof AuthorizationCode)) {
      return errorResponse("invalid_token", null);
    }

    AuthorizationCode authorizationCode = (AuthorizationCode)token;;
    if (!authorizationCode.getRedirectUri().equals(redirect_uri)) {
      logger.warn("Received redirect_uri {} does not match the one received at the authorization request.", redirect_uri);
      return errorResponse("invalid_request", "Invalid parameter value: redirect_uri");
    }

    // Verify that the client which want to use the authorization code is the client which created i
    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    if (!authorizationCode.getServiceProviderId().equals(client_id)) {
      logger.error("The serviceProvider {} wanted to access a token which belongs to the serviceProvider {}.", client_id, authorizationCode.getServiceProviderId());
      // Not a Forbidden status because it could give the information that the authorization code really exists
      return errorResponse("invalid_token", null);
    }

    IdTokenResponse response = new IdTokenResponse();

    // Create a refresh token if asked
    RefreshToken refreshToken = null;
    if (authorizationCode.getScopeIds().contains("offline_access")) {
      refreshToken = tokenHandler.createRefreshToken(account.getId(), authorizationCode);

      if (refreshToken == null) {
        return Response.serverError().build();
      }
      String refresh_token = TokenSerializer.serialize(refreshToken);

      response.setRefreshToken(refresh_token);
    }

    // Create a new access token based on the authorization code (or the new refresh token if needed)
    AccessToken accessToken = tokenHandler.createAccessToken(account.getId(), refreshToken == null ? authorizationCode : refreshToken);

    if (accessToken == null) {
      return Response.serverError().build();
    }

    String access_token = TokenSerializer.serialize(accessToken);

    long issuedAt = TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());

    response.setAccessToken(access_token);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(settings.accessTokenExpirationSeconds);
    response.setScope(SCOPE_JOINER.join(accessToken.getScopeIds()));
    response.setIdToken(JsonWebSignature.signUsingRsaSha256(
        settings.keyPair.getPrivate(),
        jsonFactory,
        JWS_HEADER,
        new IdToken.Payload()
            .setIssuer(uriInfo.getBaseUri().toString())
            .setSubject(account.getId())
            .setAudience(client_id)
            .setExpirationTimeSeconds(issuedAt + settings.idTokenExpirationSeconds)
            .setIssuedAtTimeSeconds(issuedAt)
        .setNonce(authorizationCode.getNonce())
        //.setAuthorizationTimeSeconds() // TODO: required if a max_age request is made or if auth_time is requested
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
