/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.ScopesAndClaims;
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

@Authenticated @OAuth @WithScopes(Scopes.OPENID)
@Path("/a/userinfo")
public class UserInfoEndpoint {
  /** Note: we'd prefer JWT, but OpenID Connect wants us to prefer JSON, so using qs&lt;1.0 here. */
  private static final String APPLICATION_JWT = "application/jwt; qs=0.99";

  private static final ImmutableMap<String, Function<UserAccount, Object>> CLAIM_PROVIDERS = ImmutableMap.<String, Function<UserAccount, Object>>builder()
      .put("name", UserAccount::getName)
      .put("family_name", UserAccount::getFamily_name)
      .put("given_name", UserAccount::getGiven_name)
      .put("middle_name", UserAccount::getMiddle_name)
      .put("nickname", UserAccount::getNickname)
      .put("gender", UserAccount::getGender)
      .put("birthdate", formatted(UserAccount::getBirthdate, LocalDate::toString))
      .put("locale", formatted(UserAccount::getLocale, ULocale::toLanguageTag))
      .put("email", UserAccount::getEmail_address)
      .put("email_verified", formatted(UserAccount::getEmail_verified, Boolean.TRUE::equals))
      .put("address", formatted(UserAccount::getAddress, address -> {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        putIfNotNull(result, "street_address", address.getStreet_address());
        putIfNotNull(result, "locality", address.getLocality());
        putIfNotNull(result, "region", address.getRegion());
        putIfNotNull(result, "postal_code", address.getPostal_code());
        putIfNotNull(result, "country", address.getCountry());
        return result;
      }))
      .put("phone_number", UserAccount::getPhone_number)
      .put("phone_number_verified", formatted(UserAccount::getPhone_number_verified, Boolean.TRUE::equals))
      .build();

  private static <T> Function<UserAccount, Object> formatted(Function<UserAccount, T> provider, Function<T, Object> formatter) {
    return provider.andThen(t -> t == null ? null : formatter.apply(t));
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @Inject AuthModule.Settings settings;
  @Inject AccountRepository accountRepository;
  @Inject Urls urls;

  @GET
  @Produces(APPLICATION_JWT)
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
    return uriInfo.getBaseUri().toString();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getUnsigned() {
    JwtClaims userInfo = getUserInfo();

    String json = userInfo.toJson();
    return Response.ok().entity(json).build();
  }

  @POST
  @Produces(APPLICATION_JWT)
  public Response postSigned() throws JoseException {
    return getSigned();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
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

    Set<String> claims = ScopesAndClaims.of(accessToken.getScopeIds(), accessToken.getClaimNames()).getClaimNames();

    JwtClaims userInfo = getUserInfo(userAccount, claims);
    userInfo.setSubject(userAccount.getId());
    return userInfo;
  }

  private JwtClaims getUserInfo(UserAccount userAccount, Set<String> claims) {
    JwtClaims userInfo = new JwtClaims();

    for (String claim : claims) {
      Object value = CLAIM_PROVIDERS.getOrDefault(claim, account -> null).apply(userAccount);
      if (value != null) {
        userInfo.setClaim(claim, value);
      }
    }

    long updatedAt = userAccount.getUpdated_at();
    if (updatedAt > 0) {
      userInfo.setNumericDateClaim("updated_at", NumericDate.fromMilliseconds(updatedAt));
    }

    return userInfo;
  }

  private WebApplicationException invalidTokenResponse() {
    return errorResponse(Response.Status.UNAUTHORIZED, "invalid_token");
  }

  private WebApplicationException errorResponse(Response.Status status, String errorCode) {
    return new WebApplicationException(Response.status(status).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\"").build());
  }
}
