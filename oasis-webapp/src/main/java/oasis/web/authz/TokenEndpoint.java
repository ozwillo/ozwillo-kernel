package oasis.web.authz;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Clock;
import com.google.common.base.Joiner;
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
      value = "Exchange an authorization code for an access token.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.2\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#ObtainingTokens\">OpenID Connect RFC</a> for more information."
  )
  public Response validate(MultivaluedMap<String, String> params) throws GeneralSecurityException, IOException {
    this.params = params;

    String grant_type = getRequiredParameter("grant_type");

    // TODO: support other kind of tokens (refresh_token, jwt-bearer?)
    if (!grant_type.equals("authorization_code")) {
      return errorResponse("unsupported_grant_type", null);
    }

    String code = getRequiredParameter("code");

    // Get the token behind the given code
    Token token = TokenSerializer.unserialize(code);

    if (token == null) {
      return errorResponse("invalid_token", null);
    }

    // If someone fakes a token, at least it should ensure it hasn't expired
    // It saves us a database lookup.
    if (!tokenHandler.checkTokenValidity(token)) {
      return errorResponse("invalid_token", null);
    }

    Token realToken = tokenRepository.getToken(token.getId());
    if (realToken == null || !tokenHandler.checkTokenValidity(realToken) || !(realToken instanceof AuthorizationCode)) {
      // token does not exist, is not an AccessToken, or has expired
      return errorResponse("invalid_token", null);
    }
    AuthorizationCode authorizationCode = (AuthorizationCode) realToken;

    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();

    // Verify that the client which want to use the authorization code is the client which created it
    // TODO: Validate redirect_uri
    if (!authorizationCode.getServiceProviderId().equals(client_id)) {
      logger.error("The serviceProvider {} wanted to access a token which belongs to the serviceProvider {}.",
          client_id, authorizationCode.getServiceProviderId());
      // Not a Forbidden status because it could give the information that the authorization code really exists
      return errorResponse("invalid_token", null);
    }

    Account account = accountRepository.getAccountByTokenId(token.getId());

    if (account == null  || !tokenHandler.checkTokenValidity(realToken)) {
      return errorResponse("invalid_token", null);
    }

    // Container for the new accessToken
    AccessToken accessToken = tokenHandler.createAccessToken(account.getId(), authorizationCode);

    if (accessToken == null) {
      return Response.serverError().build();
    }

    String access_token = TokenSerializer.serialize(accessToken);

    // Get scopes authorized by the user
    String scope = SCOPE_JOINER.join(accessToken.getScopeIds());

    long issuedAt = TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis());

    IdTokenResponse response = new IdTokenResponse();
    response.setAccessToken(access_token);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(settings.accessTokenExpirationSeconds);
    response.setScope(scope);
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

  private String getRequiredParameter(String paramName) {
    List<String> values = params.get(paramName);
    if (values == null || values.isEmpty()) {
      throw missingRequiredParameter(paramName);
    }
    if (values.size() != 1) {
      throw tooManyValues(paramName);
    }
    String value = values.get(0);
    if (value == null) {
      throw missingRequiredParameter(paramName);
    }
    value = value.trim();
    if (value.isEmpty()) {
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
    TokenErrorResponse response = new TokenErrorResponse();
    response.setError(error);
    response.setErrorDescription(description);
    return response(Response.Status.BAD_REQUEST, description);
  }
}
