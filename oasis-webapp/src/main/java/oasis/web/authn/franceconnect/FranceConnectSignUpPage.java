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

import static com.google.common.base.MoreObjects.firstNonNull;
import static oasis.web.authn.LoginPage.LOCALE_PARAM;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.jose4j.lang.JoseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.FranceConnectModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Address;
import oasis.model.accounts.UserAccount;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.web.authn.LoginHelper;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/a/franceconnect/signup")
public class FranceConnectSignUpPage {
  @Inject AuthModule.Settings authSettings;
  @Inject FranceConnectModule.Settings fcSettings;
  @Inject AccountRepository accountRepository;
  @Inject LoginHelper loginHelper;
  @Inject LocaleHelper localeHelper;
  @Inject Client httpClient;
  @Inject BrandRepository brandRepository;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response signUp(
      @FormParam(LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("continue") URI continueUrl,
      @FormParam("state") @DefaultValue("") String encryptedState,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    locale = localeHelper.selectLocale(locale, request);

    if (continueUrl == null || encryptedState.isEmpty()) {
      return FranceConnectCallback.badRequest(locale, Objects.toString(continueUrl, null), brandRepository.getBrandInfo(brandId));
    }

    final FranceConnectLinkState state;
    try {
      state = FranceConnectLinkState.decrypt(authSettings, encryptedState);
    } catch (JoseException | IOException e) {
      return FranceConnectCallback.badRequest(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
    }

    if (securityContext.getUserPrincipal() != null) {
      // User has signed in since we displayed the form; error out to let him retry
      // XXX: directly reprocess the FC response (encoded in 'state') as in FranceConnectCallback, for better UX
      return FranceConnectCallback.badRequest(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
    }

    Response response = httpClient.target(fcSettings.userinfoEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + state.access_token())
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .get();
    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
      return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
    }
    UserInfoResponse userInfo;
    try {
      userInfo = response.readEntity(UserInfoResponse.class);
    } catch (ProcessingException e) {
      return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
    }
    if (!state.franceconnect_sub().equals(userInfo.sub)) {
      return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
    }
    final UserAccount accountFromClaims = new UserAccount();
    accountFromClaims.setFranceconnect_sub(userInfo.sub);
    // FranceConnect will always return the 'email' claim (if requested)
    accountFromClaims.setEmail_address(userInfo.email);
    accountFromClaims.setEmail_verified(userInfo.email_verified);
    // Default to fr-FR for FranceConnect
    accountFromClaims.setLocale(firstNonNull(userInfo.locale, ULocale.FRANCE));
    // FranceConnect doesn't return a middle_name or nickname (but let's pretend it could),
    // but will always have the given_name and family_name.
    // Beware, FranceConnect will put all given names in given_name (not just the first name),
    // and uses preferred_username for the "nom d'usage" (family_name is always the name at
    // birth, e.g. maiden name). Note that this is different, in France, from a pseudonym
    // (e.g. "Johnny Hallyday" is Jean-Philippe Smet's pseudonym, not a "nom d'usage".)
    // As a result:
    //  1. we use the preferred_username (if supplied) as the family_name
    //  2. we use the name (which is the concatenation of given, middle, and family name) as the nickname
    accountFromClaims.setGiven_name(userInfo.given_name);
    accountFromClaims.setMiddle_name(userInfo.middle_name);
    accountFromClaims.setFamily_name(Optional.ofNullable(userInfo.preferred_username).orElse(userInfo.family_name));
    accountFromClaims.setNickname(Optional.ofNullable(userInfo.nickname).orElseGet(accountFromClaims::getName));
    // Other claims that FranceConnect returns, or can return (let's pretend they can always be omitted)
    accountFromClaims.setGender(userInfo.gender);
    accountFromClaims.setBirthdate(userInfo.birthdate);
    accountFromClaims.setPhone_number(userInfo.phone_number);
    accountFromClaims.setPhone_number_verified(userInfo.phone_number_verified);
    // XXX: address is nullified when stored, so no need to check if any of those claims is provided
    // XXX: note that we don't support the 'formatted' claim, but FranceConnect has the "exploded" claims anyway
    accountFromClaims.setAddress(userInfo.address);

    UserAccount account = accountRepository.createUserAccount(accountFromClaims, true);
    if (account == null) {
      // Try without the email address
      accountFromClaims.setEmail_address(null);
      accountFromClaims.setEmail_verified(null);
      account = accountRepository.createUserAccount(accountFromClaims, true);
      if (account == null) {
        // Must be that the FC identity has been linked to another account since the form was displayed.
        // XXX: directly reprocess the FC response (encoded in 'state') as in FranceConnectCallback, for better UX
        return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandRepository.getBrandInfo(brandId));
      }
    }

    return loginHelper.authenticate(account, headers, securityContext, continueUrl, state.id_token(), state.access_token(), () -> {})
        .build();
  }

  private static class UserInfoResponse {
    @JsonProperty String sub;
    @JsonProperty String email;
    @JsonProperty Boolean email_verified;
    @JsonProperty ULocale locale;
    @JsonProperty String given_name;
    @JsonProperty String middle_name;
    @JsonProperty String family_name;
    @JsonProperty String preferred_username;
    @JsonProperty String nickname;
    @JsonProperty String gender;
    @JsonProperty LocalDate birthdate;
    @JsonProperty String phone_number;
    @JsonProperty Boolean phone_number_verified;
    @JsonProperty Address address;
  }
}
