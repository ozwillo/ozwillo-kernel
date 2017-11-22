/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.web.authn.franceconnect;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.FranceConnectModule;
import oasis.model.DuplicateKeyException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.FranceConnectSoyInfo;
import oasis.soy.templates.FranceConnectSoyInfo.FranceconnectErrorSoyTemplateInfo;
import oasis.web.authn.LoginHelper;
import oasis.web.authn.SessionManagementHelper;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.Validator;
import org.jose4j.keys.HmacKey;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;

@User
@Path("/a/franceconnect/callback")
@Produces(MediaType.TEXT_HTML)
public class FranceConnectCallback {
  private static final int ACCEPTABLE_CLOCK_SKEW = 10;

  @Inject AuthModule.Settings authSettings;
  @Inject FranceConnectModule.Settings fcSettings;
  @Inject SessionManagementHelper sessionManagementHelper;
  @Inject Client httpClient;
  @Inject AccountRepository accountRepository;
  @Inject TokenRepository tokenRepository;
  @Inject LoginHelper loginHelper;
  @Inject LocaleHelper localeHelper;

  @Context UriInfo uriInfo;
  @Context HttpHeaders httpHeaders;
  @Context SecurityContext securityContext;
  @Context Request request;

  @GET
  public Response get() {
    final ImmutableMap<String, String> params = validateParams(uriInfo.getQueryParameters());
    if (params == null) {
      return badRequest(null).build();
    }

    String stateKey = params.get("state");
    if (stateKey.isEmpty()) {
      return badRequest(null).build();
    }
    Cookie stateCookie = httpHeaders.getCookies().get(FranceConnectLoginState.getCookieName(stateKey, securityContext.isSecure()));
    if (stateCookie == null) {
      // XXX: add "check that cookies are enabled in your browser" message?
      return badRequest(null).build();
    }
    return doGet(params, stateCookie.getValue())
        .cookie(FranceConnectLoginState.createExpiredCookie(stateKey, securityContext.isSecure()))
        .build();
  }

  private Response.ResponseBuilder doGet(final ImmutableMap<String, String> params, final String serializedState) {
    FranceConnectLoginState state = FranceConnectLoginState.parse(serializedState);
    if (state == null) {
      return badRequest(null);
    }

    String error = params.get("error");
    if (!error.isEmpty()) {
      // XXX: pass error/error_description/error_uri?
      return badRequest(state);
    }

    String code = params.get("code");
    if (code.isEmpty()) {
      return badRequest(state);
    }

    Response response = exchangeCodeForTokens(code);
    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
      return serverError(state);
    }
    TokenResponse tokenResponse = response.readEntity(TokenResponse.class);
    if (!"Bearer".equalsIgnoreCase(tokenResponse.token_type)) {
      return serverError(state);
    }

    String franceconnect_sub;
    try {
      JwtClaims idToken = parseIdToken(tokenResponse.id_token, state.nonce());
      franceconnect_sub = idToken.getSubject();
    } catch (InvalidJwtException | MalformedClaimException e) {
      return serverError(state);
    }

    UserAccount account = accountRepository.getUserAccountByFranceConnectSub(franceconnect_sub);
    if (account != null) {
      // There's an account linked to this FranceConnect identity
      if (securityContext.getUserPrincipal() == null) {
        // user not yet authenticated; authenticate him/her and we're all set.
        return loginHelper.authenticate(account, httpHeaders, securityContext, state.continueUrl(), tokenResponse.id_token, tokenResponse.access_token, () -> {});
      }

      SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
      if (account.getId().equals(sidToken.getAccountId())) {
        // current user matches; case of re-auth or userinfo update
        if (!tokenRepository.reAuthSidToken(sidToken.getId(), tokenResponse.id_token, tokenResponse.access_token)) { // XXX: auth_time?
          return serverError(state);
        }
        return Response.seeOther(state.continueUrl())
            .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()));
      }
      // otherwise, that's an error
      return Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_HTML_TYPE)
          .entity(new SoyTemplate(FranceConnectSoyInfo.FRANCECONNECT_ALREADY_LINKED,
              localeHelper.selectLocale(state.locale(), request)));
    } else {
      // FranceConnect identity unknown
      if (securityContext.getUserPrincipal() != null) {
        // user already authenticated, link FranceConnect identity to his account
        // XXX: display confirmation page?
        SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
        try {
          if (!accountRepository.linkToFranceConnect(sidToken.getAccountId(), franceconnect_sub)) {
            return serverError(state);
          }
        } catch (DuplicateKeyException dke) {
          // race condition
          return serverError(state);
        }
        return Response.seeOther(state.continueUrl())
            .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()));
      }

      // otherwise, ask user to authenticate (and link to account) or signup for a new account
      String email_address = getEmailAddress(tokenResponse.access_token, franceconnect_sub);
      boolean alreadyLinked = false;
      if (email_address != null && !email_address.isEmpty()) {
        UserAccount userAccount = accountRepository.getUserAccountByEmail(email_address);
        if (userAccount == null) {
          // TODO: handle not-yet-activated accounts
          email_address = null;
        } else if (userAccount.getFranceconnect_sub() != null) {
          // XXX: explicitly check for "has password"? Currently franceconnect_sub==null means you do have a password.
          alreadyLinked = true;
        } // otherwise there's a matching account, so pre-fill the form with the email address (or email address is already null)
      }
      return FranceConnectLinkPage.linkForm(authSettings,
          localeHelper.selectLocale(state.locale(), request),
          email_address, alreadyLinked, state.continueUrl().toString(),
          FranceConnectLinkState.create(tokenResponse.access_token, tokenResponse.id_token, franceconnect_sub));
    }
  }

  @Nullable
  private ImmutableMap<String, String> validateParams(MultivaluedMap<String, String> params) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (String key : new String[] { "state", "code", "error", "error_description", "error_uri" }) {
      List<String> values = params.get(key);
      String param = null;
      if (values != null) {
        for (String value : values) {
          if (value == null || value.trim().isEmpty()) {
            continue;
          }
          if (param != null) {
            // Error: duplicate values
            return null;
          }
          param = value;
        }
      }
      builder.put(key, Strings.nullToEmpty(param));
    }
    return builder.build();
  }

  private Response exchangeCodeForTokens(String code) {
    return httpClient.target(fcSettings.tokenEndpoint())
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode((fcSettings.clientId()+":"+ fcSettings.clientSecret()).getBytes(StandardCharsets.UTF_8)))
        .post(Entity.form(new Form()
            .param("grant_type", "authorization_code")
            .param("code", code)
            .param("redirect_uri", uriInfo.getBaseUriBuilder().path(FranceConnectCallback.class).build().toString())
            // For some unknown reason, FranceConnect doesn't support the *mandatory* HTTP Basic authentication.
            .param("client_id", fcSettings.clientId())
            .param("client_secret", fcSettings.clientSecret())));
  }

  private JwtClaims parseIdToken(String id_token, final String nonce) throws InvalidJwtException {
    return new JwtConsumerBuilder()
        .setJwsAlgorithmConstraints(new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, AlgorithmIdentifiers.HMAC_SHA256))
        .setVerificationKey(new HmacKey(fcSettings.clientSecret().getBytes(StandardCharsets.UTF_8)))
        .setExpectedIssuer(fcSettings.issuer())
        .setExpectedAudience(fcSettings.clientId())
        .registerValidator((Validator) jwtContext -> {
          List<String> audiences = jwtContext.getJwtClaims().getAudience();
          String azp = jwtContext.getJwtClaims().getStringClaimValue("azp");
          if (audiences.size() > 1 && azp == null) {
            return "No Authorized Party (azp) claim present, whereas the JWT contains several audiences.";
          }
          if (azp != null && !azp.equals(fcSettings.clientId())) {
            return "Authorized Party (azp) claim value (" + azp + ") does not match expected value of " + fcSettings.clientId();
          }
          return null;
        })
        .registerValidator((Validator) jwtContext -> {
          String actualNonce = jwtContext.getJwtClaims().getStringClaimValue("nonce");
          if (actualNonce == null) {
            return "No Nonce (nonce) claim present but was expecting " + nonce;
          }
          if (!actualNonce.equals(nonce)) {
            return "Nonce (nonce) claim value (" + actualNonce + ") does not match expected value of " + nonce;
          }
          return null;
        })
        .setRequireExpirationTime()
        .setRequireIssuedAt()
        .setAllowedClockSkewInSeconds(ACCEPTABLE_CLOCK_SKEW)
        .setRequireSubject()
        .build()
        .processToClaims(id_token);
  }

  @Nullable
  private String getEmailAddress(String access_token, String franceconnect_sub) {
    Response response = httpClient.target(fcSettings.userinfoEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access_token)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .get();
    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
      return null;
    }
    UserInfoResponse userInfo;
    try {
      userInfo = response.readEntity(UserInfoResponse.class);
    } catch (ProcessingException e) {
      return null;
    }
    if (!franceconnect_sub.equals(userInfo.sub)) {
      return null;
    }
    return userInfo.email;
  }

  private Response.ResponseBuilder badRequest(@Nullable FranceConnectLoginState state) {
    return error(Response.status(Response.Status.BAD_REQUEST), state);
  }

  private Response.ResponseBuilder serverError(@Nullable FranceConnectLoginState state) {
    return error(Response.serverError(), state);
  }

  private Response.ResponseBuilder error(Response.ResponseBuilder rb, @Nullable FranceConnectLoginState state) {
    return error(rb,
        localeHelper.selectLocale(state == null ? null : state.locale(), request),
        state == null ? null : state.continueUrl().toString());
  }

  static Response badRequest(ULocale locale, @Nullable String continueUrl) {
    return error(Response.status(Response.Status.BAD_REQUEST), locale, continueUrl)
        .build();
  }

  static Response serverError(ULocale locale, @Nullable String continueUrl) {
    return error(Response.serverError(), locale, continueUrl)
        .build();
  }

  private static Response.ResponseBuilder error(Response.ResponseBuilder rb, ULocale locale, @Nullable String continueUrl) {
    return rb.type(MediaType.TEXT_HTML_TYPE)
        .entity(new SoyTemplate(FranceConnectSoyInfo.FRANCECONNECT_ERROR,
            locale,
            new SoyMapData(
                FranceconnectErrorSoyTemplateInfo.FRANCECONNECT, UriBuilder.fromResource(FranceConnectLogin.class).build().toString(),
                FranceconnectErrorSoyTemplateInfo.CONTINUE, continueUrl
            )
        ));
  }

  static class TokenResponse {
    @JsonProperty String access_token;
    @JsonProperty String token_type;
    // @JsonProperty long expires_in;
    // @JsonProperty String scope;
    // @JsonProperty @Nullable String refresh_token;
    @JsonProperty @Nullable String id_token;
  }

  static class UserInfoResponse {
    @JsonProperty String sub;
    @JsonProperty String email;
    // ignore all other fields, we're only interested in the email address.
  }
}
