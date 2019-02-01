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

import static oasis.web.authn.LoginPage.log;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.security.auth.login.LoginException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogService;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.FranceConnectSoyInfo;
import oasis.soy.templates.FranceConnectSoyInfo.FranceconnectUnlinkSoyTemplateInfo;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.ChangePasswordPage;
import oasis.web.authn.LoginPage;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.security.StrictReferer;

@User
@Authenticated
@Path("/a/franceconnect/unlink")
public class FranceConnectUnlinkPage {
  private static final Logger logger = LoggerFactory.getLogger(FranceConnectUnlinkPage.class);

  @Inject AccountRepository accountRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject TokenRepository tokenRepository;
  @Inject AuditLogService auditLogService;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  public Response get(@QueryParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);
    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    return unlinkForm(Response.ok(), sidToken, null, null, brandInfo);
  }

  @POST
  @StrictReferer
  public Response post(
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    final BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    if (sidToken.getFranceconnectIdToken() != null) {
      // Special care must be taken if the user is logged in through FranceConnect.
      if (userName.isEmpty()) {
        // Cannot re-authenticate the user; but we can ignore it if the account is not linked to FranceConnect.
        UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());
        if (account.getFranceconnect_sub() == null) {
          return redirectAfterSuccess(brandInfo); // nothing to do
        }
        return unlinkForm(Response.status(Response.Status.BAD_REQUEST), sidToken, account, null, brandInfo);
      }

      UserAccount account;
      try {
        account = userPasswordAuthenticator.authenticate(userName, password);
      } catch (LoginException e) {
        log(auditLogService, userName, LoginPage.LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);
        return unlinkForm(Response.status(Response.Status.BAD_REQUEST), sidToken, null, LoginError.INCORRECT_USERNAME_OR_PASSWORD, brandInfo);
      }

      if (!account.getId().equals(sidToken.getAccountId())) {
        // Form has been tampered with, or user signed in with another account since the form was generated
        // Re-display the form: if user signed out/in, it will show the new (current) user.
        return unlinkForm(Response.status(Response.Status.BAD_REQUEST), sidToken, null, null, brandInfo);
      }
      if (!tokenRepository.reAuthSidToken(sidToken.getId(), null, null)) {
        logger.error("Error when updating SidToken {} for Account {}.", sidToken.getId(), account.getId());
        // fall-through; it's not that bad if the SidToken is not updated.
      }
    }

    if (!accountRepository.unlinkFranceConnect(sidToken.getAccountId())) {
      UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());
      return FranceConnectCallback.serverError(account.getLocale(), null, brandInfo);
    }
    return redirectAfterSuccess(brandInfo);
  }

  private Response unlinkForm(Response.ResponseBuilder rb, SidToken sidToken, @Nullable UserAccount account, LoginError error, BrandInfo brandInfo) {
    if (account == null) {
      account = accountRepository.getUserAccountById(sidToken.getAccountId());
    }
    if (account.getFranceconnect_sub() == null) {
      return redirectAfterSuccess(brandInfo);
    }

    boolean hasPassword = credentialsRepository.getCredentials(ClientType.USER, account.getId()) != null;
    if (error == null && !hasPassword) {
      error = LoginError.ACCOUNT_WITHOUT_PASSWORD;
    }

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(7)
        .put(FranceconnectUnlinkSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(FranceConnectUnlinkPage.class).build().toString())
        .put(FranceconnectUnlinkSoyTemplateInfo.INIT_PWD_URL, UriBuilder.fromResource(ChangePasswordPage.class)
                .queryParam(BrandHelper.BRAND_PARAM, brandInfo.getBrand_id())
                .build().toString())
        .put(FranceconnectUnlinkSoyTemplateInfo.AUTHND_WITHFC, sidToken.getFranceconnectIdToken() != null)
        .put(FranceconnectUnlinkSoyTemplateInfo.HAS_PASSWORD, hasPassword);

    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    urls.myProfile().ifPresent(url -> data.put(FranceconnectUnlinkSoyTemplateInfo.PORTAL_URL, url.toString()));
    if (account.getEmail_address() != null) {
      data.put(FranceconnectUnlinkSoyTemplateInfo.EMAIL, account.getEmail_address());
    }
    if (error != null) {
      data.put(FranceconnectUnlinkSoyTemplateInfo.ERROR, error.name());
    }

    return rb
        .type(MediaType.TEXT_HTML_TYPE)
        .entity(new SoyTemplate(FranceConnectSoyInfo.FRANCECONNECT_UNLINK, account.getLocale(), data.build(),
            brandInfo))
        .build();
  }

  private Response redirectAfterSuccess(BrandInfo brandInfo) {
    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    return Response.seeOther(LoginPage.defaultContinueUrl(urls.myProfile(), uriInfo)).build();
  }

  enum LoginError {
    INCORRECT_USERNAME_OR_PASSWORD,
    ACCOUNT_WITHOUT_PASSWORD
  }
}
