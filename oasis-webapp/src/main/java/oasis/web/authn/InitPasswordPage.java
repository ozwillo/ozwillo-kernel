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
package oasis.web.authn;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import oasis.auth.AuthModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.SetPasswordToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.urls.Urls;

@Path("/a/initpwd/{token}")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class InitPasswordPage {
  @Inject AccountRepository accountRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject Urls urls;
  @Inject AuthModule.Settings authSettings;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("token") String token;

  @GET
  public Response get() {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();

    SetPasswordToken setPasswordToken = tokenHandler.getCheckedToken(token, SetPasswordToken.class);
    if (setPasswordToken == null) {
      UserAccount account = accountRepository.getUserAccountById(userId);
      return SetPasswordPage.form(Response.status(Response.Status.NOT_FOUND), authSettings, urls, account, SetPasswordPage.PasswordInitError.EXPIRED_LINK);
    }

    if (!userId.equals(setPasswordToken.getAccountId())) {
      UserAccount account = accountRepository.getUserAccountById(userId);
      return SetPasswordPage.form(Response.status(Response.Status.FORBIDDEN), authSettings, urls, account, SetPasswordPage.PasswordInitError.BAD_USER);
    }

    UserAccount account = accountRepository.verifyEmailAddress(userId, false);
    credentialsRepository.saveCredentials(ClientType.USER, userId, setPasswordToken.getPwdhash(), setPasswordToken.getPwdsalt());

    tokenRepository.revokeToken(setPasswordToken.getId());

    return Response.seeOther(LoginPage.defaultContinueUrl(urls.myProfile(), uriInfo))
        .build();
  }
}
