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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.services.authn.TokenHandler;

/**
 * Implements Bearer Token authentication.
 * <p>
 * Let the request in if it doesn't contain an {@code Authorization: Bearer …} header.
 *
 * @see <a href='http://tools.ietf.org/html/rfc6750'>OAuth 2.0 Bearer Token Usage</a>
 * @see OAuthAuthenticationFilter
 */
@OAuth
@Provider
@Priority(Priorities.AUTHENTICATION - 1)
public class OAuthFilter implements ContainerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(OAuthFilter.class);

  public static final String AUTH_SCHEME = "Bearer";

  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

  @Context ResourceInfo resourceInfo;
  @Inject TokenHandler tokenHandler;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      return;
    }

    List<String> parts = AUTH_SCHEME_SPLITTER.splitToList(authorization);

    if (parts.isEmpty() || !AUTH_SCHEME.equalsIgnoreCase(parts.get(0))) {
      return;
    }
    // From now on, we know this is Bearer auth.
    if (parts.size() != 2) {
      invalidRequest(requestContext);
      return;
    }

    AccessToken accessToken = tokenHandler.getCheckedToken(parts.get(1), AccessToken.class);
    if (accessToken == null) {
      invalidToken(requestContext);
      return;
    }

    // Automatically grant "portal" scope to "portal" app
    if (ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      accessToken.setScopeIds(ImmutableSet.<String>builder()
          .addAll(accessToken.getScopeIds())
          .add(Scopes.PORTAL)
          .build());
    }

    /** Check if the resource have an annotation {@link WithScopes} **/
    WithScopes withScopesAnnotation = resourceInfo.getResourceMethod().getAnnotation(WithScopes.class);
    if (withScopesAnnotation == null) {
      withScopesAnnotation = resourceInfo.getResourceClass().getAnnotation(WithScopes.class);
      if (withScopesAnnotation == null) {
        logger.debug("Resource requires OAuth token but doesn't declare required scopes: {}",
            resourceInfo.getResourceMethod() != null ? resourceInfo.getResourceMethod() : resourceInfo.getResourceClass());
        // XXX: should we send a forbidden response rather than let the request go in?
      }
    }
    if (withScopesAnnotation != null && !accessToken.getScopeIds().containsAll(Arrays.asList(withScopesAnnotation.value()))) {
      insufficientScope(requestContext);
      return;
    }

    final OAuthPrincipal accountPrincipal = new OAuthPrincipal(accessToken);
    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();

    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public OAuthPrincipal getUserPrincipal() {
        return accountPrincipal;
      }

      @Override
      public boolean isUserInRole(String role) {
        return false;
      }

      @Override
      public boolean isSecure() {
        return oldSecurityContext.isSecure();
      }

      @Override
      public String getAuthenticationScheme() {
        return "OAUTH_BEARER";
      }
    });
  }

  private void invalidRequest(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_request\"")
        .build());
  }

  private void insufficientScope(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.FORBIDDEN)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"insufficient_scope\"")
        .build());
  }

  private void invalidToken(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_token\"")
        .build());
  }
}
