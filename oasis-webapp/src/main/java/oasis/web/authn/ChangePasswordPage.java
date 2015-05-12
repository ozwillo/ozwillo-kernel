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
package oasis.web.authn;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import com.google.common.base.Functions;
import com.google.template.soy.data.SoyMapData;

import oasis.auth.AuthModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.CredentialsService;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.ChangePasswordSoyInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.ChangePasswordSoyTemplateInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.PasswordChangedSoyTemplateInfo;
import oasis.urls.Urls;

@Path("/a/password")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class ChangePasswordPage {
  @Inject AccountRepository accountRepository;
  @Inject CredentialsService credentialsService;
  @Inject TokenRepository tokenRepository;
  @Inject AuthModule.Settings authSettings;
  @Inject Urls urls;

  @Context SecurityContext securityContext;

  @GET
  public Response get() {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    return form(Response.ok(), account, null);
  }

  @POST
  public Response post(
      @FormParam("oldpwd") String oldpwd,
      @FormParam("newpwd") @DefaultValue("") String newpwd
  ) {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    if (!credentialsService.checkPassword(ClientType.USER, userId, oldpwd)) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordChangeError.BAD_PASSWORD);
    }

    if (newpwd.length() < authSettings.passwordMinimumLength) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordChangeError.PASSWORD_TOO_SHORT);
    }
    credentialsService.setPassword(ClientType.USER, userId, newpwd);

    // revoke all sessions / tokens
    tokenRepository.revokeTokensForAccount(userId);

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure()))
        .entity(new SoyTemplate(ChangePasswordSoyInfo.PASSWORD_CHANGED,
            account.getLocale(),
            new SoyMapData(
                PasswordChangedSoyTemplateInfo.CONTINUE, urls.myOasis().transform(Functions.toStringFunction()).orNull()
            )))
        .build();
  }

  private Response form(Response.ResponseBuilder builder, UserAccount account, @Nullable PasswordChangeError error) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(ChangePasswordSoyInfo.CHANGE_PASSWORD,
            account.getLocale(),
            new SoyMapData(
                ChangePasswordSoyTemplateInfo.EMAIL, account.getEmail_address(),
                ChangePasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ChangePasswordPage.class).build().toString(),
                ChangePasswordSoyTemplateInfo.PORTAL_URL, urls.myProfile().transform(Functions.toStringFunction()).orNull(),
                ChangePasswordSoyTemplateInfo.ERROR, error == null ? null : error.name(),
                ChangePasswordSoyTemplateInfo.PWD_MIN_LENGTH, authSettings.passwordMinimumLength
            )
        ))
        .build();
  }

  enum PasswordChangeError {
    BAD_PASSWORD,
    PASSWORD_TOO_SHORT
  }
}
