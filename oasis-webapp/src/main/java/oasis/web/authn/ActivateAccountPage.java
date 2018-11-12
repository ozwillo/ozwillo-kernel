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

import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.util.ULocale;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccountActivationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.branding.BrandHelper;
import oasis.soy.templates.SignUpSoyInfo;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.utils.ResponseFactory;

@Path("/a/activate/{token}")
@Produces(MediaType.TEXT_HTML)
public class ActivateAccountPage {
  private static Logger logger = LoggerFactory.getLogger(ActivateAccountPage.class);

  @Inject AccountRepository accountRepository;
  @Inject TokenHandler tokenHandler;
  @Inject TokenRepository tokenRepository;
  @Inject MailSender mailSender;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  @Context UriInfo uriInfo;

  @PathParam("token") String token;
  @QueryParam(LoginPage.LOCALE_PARAM) @Nullable ULocale locale;

  @GET
  public Response activate(@QueryParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    AccountActivationToken accountActivationToken = tokenHandler.getCheckedToken(token, AccountActivationToken.class);
    if (accountActivationToken == null) {
      // XXX: return error message rather than blank page
      return ResponseFactory.NOT_FOUND;
    }
    UserAccount userAccount = accountRepository.verifyEmailAddress(accountActivationToken.getAccountId(), true);
    if (userAccount == null) {
      // XXX: return error message rather than blank page
      return ResponseFactory.NOT_FOUND;
    }

    tokenRepository.revokeTokensForAccount(accountActivationToken.getAccountId());

    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    URI portalUrl = LoginPage.defaultContinueUrl(urls.myOasis(), uriInfo);
    try {
      mailSender.send(new MailMessage()
          .setRecipient(userAccount.getEmail_address(), userAccount.getDisplayName())
          .setFrom(brandInfo.getMail_from())
          .setLocale(userAccount.getLocale())
          .setSubject(SignUpSoyInfo.ACCOUNT_ACTIVATED_SUBJECT)
          .setBody(SignUpSoyInfo.ACCOUNT_ACTIVATED)
          .setHtml()
          .setData(ImmutableMap.of(
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.NICKNAME, userAccount.getDisplayName(),
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.PORTAL_URL, LoginPage.defaultContinueUrl(urls.landingPage(), uriInfo).toString(),
              SignUpSoyInfo.AccountActivatedSoyTemplateInfo.MY_OZWILLO_URL, portalUrl.toString()
          )));
    } catch (MessagingException e) {
      logger.error("Error sending welcome email", e);
      // fall through: it's unfortunate but not critical.
    }

    URI continueUrl = MoreObjects.firstNonNull(accountActivationToken.getContinueUrl(), portalUrl);
    return Response.seeOther(continueUrl).build();
  }
}
