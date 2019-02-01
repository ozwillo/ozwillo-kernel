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

import static oasis.web.authn.LoginPage.LOCALE_PARAM;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.security.auth.login.LoginException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jose4j.lang.JoseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.auditlog.AuditLogService;
import oasis.auth.AuthModule;
import oasis.model.DuplicateKeyException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.FranceConnectSoyInfo;
import oasis.soy.templates.FranceConnectSoyInfo.FranceconnectLinkSoyTemplateInfo;
import oasis.web.authn.ForgotPasswordPage;
import oasis.web.authn.LoginHelper;
import oasis.web.authn.LoginPage;
import oasis.web.authn.User;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@User
@Path("/a/franceconnect/link")
public class FranceConnectLinkPage {
  @Inject AuthModule.Settings authSettings;
  @Inject AccountRepository accountRepository;
  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject AuditLogService auditLogService;
  @Inject LoginHelper loginHelper;
  @Inject LocaleHelper localeHelper;
  @Inject BrandRepository brandRepository;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  @POST
  @StrictReferer
  public Response post(
      @FormParam(LOCALE_PARAM) @Nullable ULocale locale,
      @FormParam("continue") URI continueUrl,
      @FormParam("state") @DefaultValue("") String encryptedState,
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    locale = localeHelper.selectLocale(locale, request);

    if (continueUrl == null || encryptedState.isEmpty()) {
      return FranceConnectCallback.badRequest(locale, Objects.toString(continueUrl, null), brandInfo);
    }

    final FranceConnectLinkState state;
    try {
      state = FranceConnectLinkState.decrypt(authSettings, encryptedState);
    } catch (JoseException | IOException e) {
      return FranceConnectCallback.badRequest(locale, continueUrl.toString(), brandInfo);
    }

    if (securityContext.getUserPrincipal() != null) {
      // User has signed in since we displayed the form; error out to let him retry
      // XXX: directly reprocess the FC response (encoded in 'state') as in FranceConnectCallback, for better UX
      return FranceConnectCallback.badRequest(locale, continueUrl.toString(), brandInfo);
    }

    UserAccount account;
    try {
      account = userPasswordAuthenticator.authenticate(userName, password);
    } catch (LoginException e) {
      LoginPage.log(auditLogService, userName, LoginPage.LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);
      return linkForm(locale, continueUrl, encryptedState, LoginError.INCORRECT_USERNAME_OR_PASSWORD, brandInfo);
    }

    try {
      if (!accountRepository.linkToFranceConnect(account.getId(), state.franceconnect_sub())) {
        return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandInfo);
      }
    } catch (DuplicateKeyException dke) {
      // race condition
      return FranceConnectCallback.serverError(locale, continueUrl.toString(), brandInfo);
    }

    return loginHelper.authenticate(account, headers, securityContext, continueUrl, state.id_token(), state.access_token(), () -> {
      LoginPage.log(auditLogService, userName, LoginPage.LoginLogEvent.LoginResult.AUTHENTICATION_SUCCEEDED);
    }).build();
  }

  private Response linkForm(ULocale locale, URI continueUrl, String state, @Nullable LoginError error, BrandInfo brandInfo) {
    return linkForm(Response.status(Response.Status.BAD_REQUEST), locale, null, false, continueUrl.toString(), state, error, brandInfo)
        .build();
  }

  static Response.ResponseBuilder linkForm(AuthModule.Settings settings, ULocale locale, @Nullable String email, boolean alreadyLinked, String continueUrl, FranceConnectLinkState state, BrandInfo brandInfo) {
    try {
      return linkForm(Response.ok(), locale, email, alreadyLinked, continueUrl, state.encrypt(settings), null, brandInfo);
    } catch (JoseException | JsonProcessingException e) {
      return Response.serverError();
    }
  }

  private static Response.ResponseBuilder linkForm(Response.ResponseBuilder rb, ULocale locale, @Nullable String email, boolean alreadyLinked, String continueUrl, String state, @Nullable LoginError error, BrandInfo brandInfo) {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(8)
        .put(FranceconnectLinkSoyTemplateInfo.SIGN_UP_FORM_ACTION, UriBuilder.fromResource(FranceConnectSignUpPage.class).build().toString())
        .put(FranceconnectLinkSoyTemplateInfo.LOGIN_FORM_ACTION, UriBuilder.fromResource(FranceConnectLinkPage.class).build().toString())
        .put(FranceconnectLinkSoyTemplateInfo.FORGOT_PASSWORD, UriBuilder.fromResource(ForgotPasswordPage.class)
                .queryParam(LOCALE_PARAM, locale.toLanguageTag())
                .queryParam(BrandHelper.BRAND_PARAM, brandInfo.getBrand_id())
                .build().toString())
        .put(FranceconnectLinkSoyTemplateInfo.ALREADY_LINKED, alreadyLinked)
        .put(FranceconnectLinkSoyTemplateInfo.CONTINUE, continueUrl)
        .put(FranceconnectLinkSoyTemplateInfo.ENCRYPTED_STATE, state);
    if (email != null) {
      data.put(FranceconnectLinkSoyTemplateInfo.EMAIL, email);
    }
    if (error != null) {
      data.put(FranceconnectLinkSoyTemplateInfo.ERROR, error.name());
    }

    return rb
        .type(MediaType.TEXT_HTML_TYPE)
        .entity(new SoyTemplate(FranceConnectSoyInfo.FRANCECONNECT_LINK, locale, data.build(), brandInfo));
  }

  enum LoginError {
    INCORRECT_USERNAME_OR_PASSWORD
  }
}
