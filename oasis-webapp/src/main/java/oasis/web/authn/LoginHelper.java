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

import java.net.URI;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.cookies.CookieFactory;
import oasis.web.utils.UserAgentFingerprinter;

public class LoginHelper {
  private static final Logger logger = LoggerFactory.getLogger(LoginHelper.class);

  private final TokenHandler tokenHandler;
  private final UserAgentFingerprinter fingerprinter;
  private final SessionManagementHelper sessionManagementHelper;
  private final ClientCertificateHelper clientCertificateHelper;

  @Inject
  public LoginHelper(TokenHandler tokenHandler, UserAgentFingerprinter fingerprinter,
      SessionManagementHelper sessionManagementHelper, ClientCertificateHelper clientCertificateHelper) {
    this.tokenHandler = tokenHandler;
    this.fingerprinter = fingerprinter;
    this.sessionManagementHelper = sessionManagementHelper;
    this.clientCertificateHelper = clientCertificateHelper;
  }

  public Response.ResponseBuilder authenticate(UserAccount account, HttpHeaders headers, SecurityContext securityContext, URI continueUrl,
      @Nullable String franceconnectIdToken, Runnable onSuccess) {
    String pass = tokenHandler.generateRandom();
    byte[] fingerprint = fingerprinter.fingerprint(headers);
    SidToken sidToken = tokenHandler.createSidToken(account.getId(), fingerprint, hasClientCertificate(account.getId(), headers), franceconnectIdToken, pass);
    if (sidToken == null) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("No SidToken was created for Account {}.", account.getId());
      return Response.serverError();
    }

    onSuccess.run();

    // TODO: One-Time Password
    return Response
        .seeOther(continueUrl)
        .cookie(CookieFactory.createSessionCookie(UserFilter.COOKIE_NAME, TokenSerializer.serialize(sidToken, pass), securityContext.isSecure(), true)) // TODO: remember me
        .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()));
  }

  private boolean hasClientCertificate(String accountId, HttpHeaders headers) {
    final ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(headers.getRequestHeaders());
    return clientCertificate != null
        && clientCertificate.getClient_type() == ClientType.USER
        && Objects.equals(clientCertificate.getClient_id(), accountId);
  }
}
