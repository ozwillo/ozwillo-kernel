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
package oasis.web.auditlog;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.HttpAuditLogEvent;
import oasis.model.authn.AccessToken;
import oasis.model.authn.SidToken;
import oasis.web.authn.ClientPrincipal;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.UserSessionPrincipal;

@Provider
@Priority(Integer.MIN_VALUE)
public class HttpInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String START_TIME_PROP = HttpInterceptor.class.getName() + ".startTime";

  private static final ImmutableSet<String> HTTP_HEADERS_TO_LOG = ImmutableSet.of(
      HttpHeaders.ACCEPT,
      HttpHeaders.ACCEPT_CHARSET,
      HttpHeaders.ACCEPT_ENCODING,
      HttpHeaders.ACCEPT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE,
      HttpHeaders.CONTENT_LENGTH,
      HttpHeaders.USER_AGENT,
      "Origin",
      "Referer"
  );

  @Inject AuditLogService auditLogService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setProperty(START_TIME_PROP, System.nanoTime());
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    HttpAuditLogEvent event = auditLogService.event(HttpAuditLogEvent.class)
        .setUrl(requestContext.getUriInfo().getRequestUri().toString())
        .setMethod(requestContext.getMethod());

    event.setHeaders(requestContext.getHeaders().entrySet().stream()
        .filter(entry -> HTTP_HEADERS_TO_LOG.contains(entry.getKey()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));

    event.setStatus(responseContext.getStatus());

    Long startTimeNanos = (Long) requestContext.getProperty(START_TIME_PROP);
    if (startTimeNanos != null) {
      long elapsedNanos = System.nanoTime() - startTimeNanos;
      long endTimeMillis = System.currentTimeMillis();
      long startTimeMillis = endTimeMillis - TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

      event.setStartTime(startTimeMillis)
          .setEndTime(endTimeMillis)
          .setElapsedTime(elapsedNanos);
    }

    event.setAuthenticationScheme(requestContext.getSecurityContext().getAuthenticationScheme());
    Principal principal = requestContext.getSecurityContext().getUserPrincipal();
    // TODO: make it more general; possibly let each auth filter append log data to the request
    if (principal instanceof UserSessionPrincipal) {
      SidToken sidToken = ((UserSessionPrincipal) principal).getSidToken();
      event.setRemoteUser(sidToken.getAccountId());
    } else if (principal instanceof ClientPrincipal) {
      event.setRemoteClient(((ClientPrincipal) principal).getClientId());
    } else if (principal instanceof OAuthPrincipal) {
      AccessToken accessToken = ((OAuthPrincipal) principal).getAccessToken();
      event.setRemoteUser(accessToken.getAccountId())
          .setRemoteClient(accessToken.getServiceProviderId());
    } else if (principal != null) {
      event.setRemoteUser(principal.getName());
    }


    event.log();
  }
}
