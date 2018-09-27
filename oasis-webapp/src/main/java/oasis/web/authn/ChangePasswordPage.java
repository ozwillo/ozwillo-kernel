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
package oasis.web.authn;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import com.google.common.collect.ImmutableMap;

import oasis.auth.AuthModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.CredentialsService;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.ChangePasswordSoyInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.ChangePasswordSoyTemplateInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.PasswordChangedSoyTemplateInfo;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.security.StrictReferer;

@Path("/a/password")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class ChangePasswordPage {
  @Inject AccountRepository accountRepository;
  @Inject CredentialsService credentialsService;
  @Inject CredentialsRepository credentialsRepository;
  @Inject TokenRepository tokenRepository;
  @Inject AuthModule.Settings authSettings;
  @Inject SessionManagementHelper sessionManagementHelper;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  @Context SecurityContext securityContext;

  @GET
  public Response get(@QueryParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    if (credentialsRepository.getCredentials(ClientType.USER, account.getId()) == null) {
      Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
      return SetPasswordPage.form(Response.ok(), authSettings, urls, account, null, brandInfo);
    }
    return form(Response.ok(), account, null, brandInfo);
  }

  @POST @StrictReferer
  public Response post(
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId,
      @FormParam("oldpwd") String oldpwd,
      @FormParam("newpwd") @DefaultValue("") String newpwd
  ) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    if (!credentialsService.checkPassword(ClientType.USER, userId, oldpwd)) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordChangeError.BAD_PASSWORD, brandInfo);
    }

    if (newpwd.length() < authSettings.passwordMinimumLength) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordChangeError.PASSWORD_TOO_SHORT, brandInfo);
    }
    credentialsService.setPassword(ClientType.USER, userId, newpwd);

    // revoke all sessions / tokens
    tokenRepository.revokeTokensForAccount(userId);

    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure(), true))
        .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()))
        .entity(new SoyTemplate(ChangePasswordSoyInfo.PASSWORD_CHANGED,
            account.getLocale(),
            urls.myOasis()
                .map(url -> ImmutableMap.of(PasswordChangedSoyTemplateInfo.CONTINUE, url.toString()))
                .orElse(null),
            brandInfo
            ))
        .build();
  }

  private Response form(Response.ResponseBuilder builder, UserAccount account, @Nullable PasswordChangeError error, BrandInfo brandInfo) {
    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    return form(builder, authSettings, urls, account, error, brandInfo);
  }

  static Response form(Response.ResponseBuilder builder, AuthModule.Settings authSettings, Urls urls, UserAccount account, @Nullable PasswordChangeError error,
      BrandInfo brandInfo) {
    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(5)
        .put(ChangePasswordSoyTemplateInfo.EMAIL, account.getEmail_address())
        .put(ChangePasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ChangePasswordPage.class).build().toString())
        .put(ChangePasswordSoyTemplateInfo.PWD_MIN_LENGTH, authSettings.passwordMinimumLength);
    urls.myProfile().ifPresent(url -> data.put(ChangePasswordSoyTemplateInfo.PORTAL_URL, url.toString()));
    if (error != null) {
      data.put(ChangePasswordSoyTemplateInfo.ERROR, error.name());
    }

    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(ChangePasswordSoyInfo.CHANGE_PASSWORD, account.getLocale(), data.build(), brandInfo))
        .build();
  }

  enum PasswordChangeError {
    BAD_PASSWORD,
    PASSWORD_TOO_SHORT
  }
}
