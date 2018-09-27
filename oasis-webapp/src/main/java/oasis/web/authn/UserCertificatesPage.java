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
import javax.ws.rs.core.UriInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.UserCertificatesSoyInfo;
import oasis.urls.Urls;
import oasis.web.authn.ClientCertificateHelper.ClientCertificateData;
import oasis.web.security.StrictReferer;

@Path("/a/certs")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class UserCertificatesPage {
  @Inject AccountRepository accountRepository;
  @Inject ClientCertificateRepository clientCertificateRepository;
  @Inject ClientCertificateHelper clientCertificateHelper;
  @Inject Urls urls;
  @Inject BrandRepository brandRepository;

  @Context SecurityContext securityContext;
  @Context HttpHeaders headers;

  @GET
  @Path("")
  public Response get(@QueryParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);
    String accountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    return form(false, accountId, clientCertificateHelper.getClientCertificateData(headers.getRequestHeaders()), brandInfo);
  }

  @POST
  @StrictReferer
  @Path("/add")
  public Response addCurrent(
      @Context UriInfo uriInfo,
      @FormParam("subject") String subject,
      @FormParam("issuer") String issuer,
      @FormParam("continue") @Nullable URI continueUrl,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    if (continueUrl != null) {
      continueUrl = uriInfo.getBaseUri().relativize(continueUrl);
      if (continueUrl.isAbsolute() || continueUrl.isOpaque()) {
        // continueUrl must have been absolute (or opaque) already; reject (ignore) it.
        continueUrl = null;
      }
    }

    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();

    ClientCertificateData clientCertificateData = clientCertificateHelper.getClientCertificateData(headers.getRequestHeaders());
    if (clientCertificateData == null) {
      // No certificate, we shouldn't have received that request (unless the certificate was removed between page loads)
      return redirectOrErrorForm(continueUrl, sidToken.getAccountId(), null, brandInfo);
    }
    if (!clientCertificateData.getSubjectDN().equals(subject) ||
        !clientCertificateData.getIssuerDN().equals(issuer)) {
      // Bad certificate (must have been swapped between page loads, or form data being tampered)
      return redirectOrErrorForm(continueUrl, sidToken.getAccountId(), clientCertificateData, brandInfo);
    }

    if (sidToken.isUsingClientCertificate()) {
      // Certificate already registered with the account
      return redirect(continueUrl);
    }

    ClientCertificate clientCertificate = new ClientCertificate();
    clientCertificate.setSubject_dn(clientCertificateData.getSubjectDN());
    clientCertificate.setIssuer_dn(clientCertificateData.getIssuerDN());
    clientCertificate.setClient_type(ClientType.USER);
    clientCertificate.setClient_id(sidToken.getAccountId());
    clientCertificate = clientCertificateRepository.saveClientCertificate(clientCertificate);
    if (clientCertificate == null) {
      // Certificate already linked (must be to other account), reject (we shouldn't have received that request)
      return redirectOrErrorForm(continueUrl, sidToken.getAccountId(), clientCertificateData, brandInfo);
    }
    return redirect(continueUrl);
  }

  private Response redirect(@Nullable URI continueUrl) {
    return Response.seeOther(continueUrl != null
        ? continueUrl
        : UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "get").build()
    ).build();
  }

  private Response redirectOrErrorForm(@Nullable URI continueUrl, String accountId, @Nullable ClientCertificateData clientCertificateData, BrandInfo brandInfo) {
    return continueUrl != null
        ? Response.seeOther(continueUrl).build()
        : form(true, accountId, clientCertificateData, brandInfo);
  }

  @POST
  @StrictReferer
  @Path("/delete")
  public Response remove(
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId,
      @FormParam("id") String id) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    String accountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();

    boolean success = clientCertificateRepository.deleteClientCertificate(ClientType.USER, accountId, id);
    if (success) {
      return Response.seeOther(UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "get").build()).build();
    }
    return form(true, accountId, clientCertificateHelper.getClientCertificateData(headers.getRequestHeaders()), brandInfo);
  }

  private Response form(boolean error, String accountId, @Nullable ClientCertificateData clientCertificateData, BrandInfo brandInfo) {
    UserAccount account = accountRepository.getUserAccountById(accountId);

    ImmutableMap<String, Object> currentCert;
    if (clientCertificateData != null) {
      ClientCertificate currentCertificate = clientCertificateRepository.getClientCertificate(
          clientCertificateData.getSubjectDN(), clientCertificateData.getIssuerDN());
      if (currentCertificate != null) {
        boolean linkedToOtherAccount = currentCertificate.getClient_type() != ClientType.USER ||
            !currentCertificate.getClient_id().equals(accountId);
        currentCert = ImmutableMap.of(
            UserCertificatesSoyInfo.Param.ID, currentCertificate.getId(),
            UserCertificatesSoyInfo.Param.SUBJECT, currentCertificate.getSubject_dn(),
            UserCertificatesSoyInfo.Param.ISSUER, currentCertificate.getIssuer_dn(),
            UserCertificatesSoyInfo.Param.LINKED_TO_OTHER_ACCOUNT, linkedToOtherAccount
        );
      } else {
        currentCert = ImmutableMap.of(
            UserCertificatesSoyInfo.Param.SUBJECT, clientCertificateData.getSubjectDN(),
            UserCertificatesSoyInfo.Param.ISSUER, clientCertificateData.getIssuerDN()
        );
      }
    } else {
      currentCert = null;
    }

    ImmutableList<ImmutableMap<String, String>> clientCerts = Streams.stream(clientCertificateRepository.getClientCertificatesForClient(ClientType.USER, accountId))
        .map(clientCertificate -> ImmutableMap.of(
          UserCertificatesSoyInfo.Param.ID, clientCertificate.getId(),
          UserCertificatesSoyInfo.Param.SUBJECT, clientCertificate.getSubject_dn(),
          UserCertificatesSoyInfo.Param.ISSUER, clientCertificate.getIssuer_dn()
        ))
        .collect(ImmutableList.toImmutableList());

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(7)
        .put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.ERROR, error)
        .put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.EMAIL, account.getEmail_address())
        .put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.ADD_FORM_ACTION, UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "addCurrent").build().toString())
        .put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.DELETE_FORM_ACTION, UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "remove").build().toString())
        .put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.CERTS, clientCerts);
    if (currentCert != null) {
      data.put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.CURRENT_CERT, currentCert);
    }
    urls.myProfile().ifPresent(url -> data.put(UserCertificatesSoyInfo.UserCertificatesSoyTemplateInfo.PORTAL_URL, url.toString()));

    return (error ? Response.status(Response.Status.BAD_REQUEST) : Response.ok())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(UserCertificatesSoyInfo.USER_CERTIFICATES, account.getLocale(), data.build(),
            brandInfo))
        .build();
  }
}
