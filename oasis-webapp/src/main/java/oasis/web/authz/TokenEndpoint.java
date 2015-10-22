/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.authz;

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

import org.joda.time.DateTimeUtils;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.auth.AuthModule;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AbstractOAuthToken;
import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.Scopes;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.openidconnect.ErrorResponse;
import oasis.web.resteasy.Resteasy1099;

@Path("/a/token")
@Authenticated @Client
@Api(value = "/a/token", description = "Token Endpoint.")
public class TokenEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(TokenEndpoint.class);
  private static final Joiner SCOPE_JOINER = Joiner.on(' ').skipNulls();
  private static final Splitter SCOPE_SPLITTER = Splitter.on(' ');

  @Inject AuthModule.Settings settings;
  @Inject DateTimeUtils.MillisProvider clock;

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AuditLogService auditLogService;
  @Inject Urls urls;

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
  public Response validate(MultivaluedMap<String, String> params) throws JoseException {
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

  private Response validateRefreshToken() {
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

    log(accessToken, TokenLogEvent.GrantType.refresh_token);

    String access_token = TokenSerializer.serialize(accessToken, pass);

    TokenResponse response = new TokenResponse();
    response.access_token = access_token;
    response.token_type = "Bearer";
    response.expires_in = accessToken.expiresIn().getStandardSeconds();
    response.scope = SCOPE_JOINER.join(asked_scopes);

    return response(Response.Status.OK, response);
  }

  private Response validateAuthorizationCode() throws JoseException {
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

    TokenResponse response = new TokenResponse();

    AccessToken accessToken;
    final String pass = tokenHandler.generateRandom();
    if (authorizationCode.getScopeIds().contains(Scopes.OFFLINE_ACCESS)) {
      String refreshPass = tokenHandler.generateRandom();
      RefreshToken refreshToken = tokenHandler.createRefreshToken(authorizationCode, refreshPass);

      if (refreshToken == null) {
        return Response.serverError().build();
      }

      log(refreshToken);

      response.refresh_token = TokenSerializer.serialize(refreshToken, refreshPass);

      accessToken = tokenHandler.createAccessToken(refreshToken, refreshToken.getScopeIds(), pass);
    } else {
      accessToken = tokenHandler.createAccessToken(authorizationCode, pass);
    }

    if (accessToken == null) {
      return Response.serverError().build();
    }

    log(accessToken, TokenLogEvent.GrantType.authorization_code);

    String access_token = TokenSerializer.serialize(accessToken, pass);

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

    response.access_token = access_token;
    response.token_type = "Bearer";
    response.expires_in = accessToken.expiresIn().getStandardSeconds();
    response.scope = SCOPE_JOINER.join(accessToken.getScopeIds());

    long issuedAt = TimeUnit.MILLISECONDS.toSeconds(clock.getMillis());
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(getIssuer());
    claims.setSubject(accessToken.getAccountId());
    claims.setAudience(client_id);
    claims.setIssuedAt(NumericDate.fromSeconds(issuedAt));
    claims.setExpirationTime(NumericDate.fromSeconds(issuedAt + settings.idTokenDuration.getStandardSeconds()));
    claims.setClaim("nonce", authorizationCode.getNonce());
    if (authTime != null) {
      claims.setClaim("auth_time", authTime);
    }
    if (isAppUser) {
      claims.setClaim("app_user", Boolean.TRUE);
    }
    if (isAppAdmin) {
      claims.setClaim("app_admin", Boolean.TRUE);
    }
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());

    response.id_token = jws.getCompactSerialization();

    return response(Response.Status.OK, response);
  }

  private String getIssuer() {
    if (urls.canonicalBaseUri().isPresent()) {
      return urls.canonicalBaseUri().get().toString();
    }
    return Resteasy1099.getBaseUri(uriInfo).toString();
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
    ErrorResponse response = new ErrorResponse()
        .setError(error)
        .setError_description(description);
    return response(Response.Status.BAD_REQUEST, response);
  }

  private void log(AccessToken token, TokenLogEvent.GrantType grantType) {
    _log(token, TokenLogEvent.TokenType.access_token, grantType);
  }

  private void log(RefreshToken token) {
    _log(token, TokenLogEvent.TokenType.refresh_token, TokenLogEvent.GrantType.authorization_code);
  }

  private void _log(AbstractOAuthToken token, TokenLogEvent.TokenType tokenType, TokenLogEvent.GrantType grantType) {
    auditLogService.event(TokenLogEvent.class)
        .setGrantType(grantType)
        .setTokenType(tokenType)
        .setRemoteUser(token.getAccountId())
        .setRemoteClient(token.getServiceProviderId())
        .setScope(ImmutableSet.copyOf(token.getScopeIds()))
        .log();
  }

  public static class TokenLogEvent extends AuditLogEvent {
    public TokenLogEvent() {
      super("issued_token_event");
    }

    public TokenLogEvent setGrantType(GrantType grantType) {
      this.addContextData("grant_type", grantType);
      return this;
    }

    public TokenLogEvent setTokenType(TokenType tokenType) {
      this.addContextData("token_type", tokenType);
      return this;
    }

    public TokenLogEvent setRemoteUser(String remoteUser) {
      this.addContextData("remote_user", remoteUser);
      return this;
    }

    public TokenLogEvent setRemoteClient(String remoteClient) {
      this.addContextData("remote_client", remoteClient);
      return this;
    }

    public TokenLogEvent setScope(ImmutableSet<String> scopes) {
      this.addContextData("scope", scopes);
      return this;
    }

    public static enum GrantType {
      authorization_code,
      refresh_token,
    }

    public static enum TokenType {
      access_token,
      refresh_token,
    }
  }

  static class TokenResponse {
    @JsonProperty String access_token;
    @JsonProperty String token_type;
    @JsonProperty long expires_in;
    @JsonProperty String scope;
    @JsonProperty @Nullable String refresh_token;
    @JsonProperty @Nullable String id_token;
  }
}
