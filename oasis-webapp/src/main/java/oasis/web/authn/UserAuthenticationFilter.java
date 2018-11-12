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

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import com.google.common.net.UrlEscapers;
import com.ibm.icu.util.ULocale;

import oasis.model.branding.BrandInfo;
import oasis.services.branding.BrandHelper;

@Authenticated @User
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserAuthenticationFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    Principal principal = requestContext.getSecurityContext().getUserPrincipal();
    if (principal == null) {
      // TODO: One-Time Password
      loginResponse(requestContext);
    }
  }

  private void loginResponse(ContainerRequestContext requestContext) {
    String brandId = requestContext.getUriInfo().getQueryParameters().getFirst(BrandHelper.BRAND_PARAM);

    requestContext.abortWith(loginResponse(requestContext.getUriInfo().getRequestUri(), null, null,
        brandId == null ? BrandInfo.DEFAULT_BRAND : brandId));
  }

  public static Response loginResponse(URI continueUrl, ULocale locale, @Nullable String cancelUrl, String brandId) {
    final UriBuilder redirectUri = UriBuilder
        .fromResource(LoginPage.class)
        .queryParam(LoginPage.CONTINUE_PARAM, UrlEscapers.urlFormParameterEscaper().escape(continueUrl.toString()))
        .queryParam(BrandHelper.BRAND_PARAM, brandId);
    if (locale != null) {
      redirectUri.queryParam(LoginPage.LOCALE_PARAM, UrlEscapers.urlFormParameterEscaper().escape(locale.toLanguageTag()));
    }
    if (cancelUrl != null) {
      redirectUri.queryParam(LoginPage.CANCEL_PARAM, UrlEscapers.urlFormParameterEscaper().escape(cancelUrl));
    }
    return Response
        .seeOther(redirectUri.build())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
        .build();
  }
}
