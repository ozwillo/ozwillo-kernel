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
package oasis.web.userinfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.auth.AuthModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.authz.KeysEndpoint;
import oasis.web.resteasy.Resteasy1099;

@Authenticated @OAuth @WithScopes(Scopes.OPENID)
@Path("/a/userinfo")
@Api(value = "/a/userinfo", description = "UserInfo Endpoint")
public class UserInfoEndpoint {
  private static final DateTimeFormatter BIRTHDATE_FORMATTER = ISODateTimeFormat.date().withDefaultYear(0);
  /** Note: we'd prefer JWT, but OpenID Connect wants us to prefer JSON, so using qs&lt;1.0 here. */
  private static final String APPLICATION_JWT = "application/jwt; qs=0.99";

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @Inject AuthModule.Settings settings;
  @Inject AccountRepository accountRepository;
  @Inject Urls urls;

  @GET
  @Produces(APPLICATION_JWT)
  @ApiOperation(
      value = "Return Claims about the End-User in signed JWT format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a>, " +
          "the <a href=\"http://tools.ietf.org/html/draft-ietf-oauth-json-web-token-08\">JWT Draft</a> " +
          "and the <a href=\"http://tools.ietf.org/html/draft-ietf-jose-json-web-signature-11\">JWS Draft</a> for more information."
  )
  public Response getSigned() throws JoseException {
    JwtClaims userInfo = getUserInfo();
    userInfo.setIssuer(getIssuer());
    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    userInfo.setAudience(accessToken.getServiceProviderId());

    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(userInfo.toJson());

    String signedJwt = jws.getCompactSerialization();
    return Response.ok().entity(signedJwt).build();
  }

  private String getIssuer() {
    if (urls.canonicalBaseUri().isPresent()) {
      return urls.canonicalBaseUri().get().toString();
    }
    return Resteasy1099.getBaseUri(uriInfo).toString();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Return Claims about the End-User in JSON format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a> for more information."
  )
  public Response getUnsigned() {
    JwtClaims userInfo = getUserInfo();

    String json = userInfo.toJson();
    return Response.ok().entity(json).build();
  }

  @POST
  @Produces(APPLICATION_JWT)
  @ApiOperation(
      value = "Return Claims about the End-User in signed JWT format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a>, " +
          "the <a href=\"http://tools.ietf.org/html/draft-ietf-oauth-json-web-token-08\">JWT Draft</a> " +
          "and the <a href=\"http://tools.ietf.org/html/draft-ietf-jose-json-web-signature-11\">JWS Draft</a> for more information."
  )
  public Response postSigned() throws JoseException {
    return getSigned();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Return Claims about the End-User in JSON format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a> for more information."
  )
  public Response postUnsigned() {
    return getUnsigned();
  }

  private JwtClaims getUserInfo() {
    OAuthPrincipal oAuthPrincipal = (OAuthPrincipal) securityContext.getUserPrincipal();
    UserAccount userAccount = accountRepository.getUserAccountById(oAuthPrincipal.getAccessToken().getAccountId());

    if (userAccount == null) {
      throw invalidTokenResponse();
    }

    AccessToken accessToken = oAuthPrincipal.getAccessToken();
    assert accessToken != null;

    Set<String> scopeIds = accessToken.getScopeIds();

    JwtClaims userInfo = getUserInfo(userAccount, scopeIds);
    userInfo.setSubject(userAccount.getId());
    return userInfo;
  }

  private JwtClaims getUserInfo(UserAccount userAccount, Set<String> scopeIds) {
    JwtClaims userInfo = new JwtClaims();

    if (scopeIds.contains(Scopes.PROFILE)) {
      setClaimIfNotNull(userInfo, "name", userAccount.getName());
      setClaimIfNotNull(userInfo, "family_name", userAccount.getFamily_name());
      setClaimIfNotNull(userInfo, "given_name", userAccount.getGiven_name());
      setClaimIfNotNull(userInfo, "middle_name", userAccount.getMiddle_name());
      setClaimIfNotNull(userInfo, "nickname", userAccount.getNickname());
      setClaimIfNotNull(userInfo, "picture", userAccount.getPicture());
      setClaimIfNotNull(userInfo, "gender", userAccount.getGender());
      if (userAccount.getBirthdate() != null) {
        userInfo.setClaim("birthdate", userAccount.getBirthdate().toString(BIRTHDATE_FORMATTER));
      }
      setClaimIfNotNull(userInfo, "zoneinfo", userAccount.getZoneinfo());
      if (userAccount.getLocale() != null) {
        setClaimIfNotNull(userInfo, "locale", userAccount.getLocale().toLanguageTag());
      }
    }

    if (scopeIds.contains(Scopes.EMAIL) && userAccount.getEmail_address() != null) {
      userInfo.setClaim("email", userAccount.getEmail_address());
      userInfo.setClaim("email_verified", Boolean.TRUE.equals(userAccount.getEmail_verified()));
    }

    if (scopeIds.contains(Scopes.ADDRESS) && userAccount.getAddress() != null) {
      LinkedHashMap<String, Object> address = new LinkedHashMap<>();
      putIfNotNull(address, "street_address", userAccount.getAddress().getStreet_address());
      putIfNotNull(address, "locality", userAccount.getAddress().getLocality());
      putIfNotNull(address, "region", userAccount.getAddress().getRegion());
      putIfNotNull(address, "postal_code", userAccount.getAddress().getPostal_code());
      putIfNotNull(address, "country", userAccount.getAddress().getCountry());
      userInfo.setClaim("address", address);
    }

    if (scopeIds.contains(Scopes.PHONE) && userAccount.getPhone_number() != null) {
      userInfo.setClaim("phone_number", userAccount.getPhone_number());
      userInfo.setClaim("phone_number_verified", Boolean.TRUE.equals(userAccount.getPhone_number_verified()));
    }

    long updatedAt = userAccount.getUpdated_at();
    if (updatedAt > 0) {
      userInfo.setNumericDateClaim("updated_at", NumericDate.fromMilliseconds(updatedAt));
    }

    return userInfo;
  }

  private void setClaimIfNotNull(JwtClaims claims, String claimName, Object value) {
    if (value != null) {
      claims.setClaim(claimName, value);
    }
  }

  private void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private WebApplicationException invalidTokenResponse() {
    return errorResponse(Response.Status.UNAUTHORIZED, "invalid_token");
  }

  private WebApplicationException errorResponse(Response.Status status, String errorCode) {
    return new WebApplicationException(Response.status(status).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\"").build());
  }
}
