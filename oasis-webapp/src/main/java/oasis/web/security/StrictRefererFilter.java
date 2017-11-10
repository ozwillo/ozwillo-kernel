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
package oasis.web.security;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableSet;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.auth.AuthModule;
import oasis.services.security.OriginHelper;

/** @see StrictRefererFeature */
public class StrictRefererFilter implements ContainerRequestFilter {
  static final String REFERER_HEADER = "Referer";
  static final String ORIGIN_HEADER = "Origin";

  @Inject AuthModule.Settings settings;
  @Inject AuditLogService auditLogService;

  @Context ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    ImmutableSet<String> expectedOrigins = getExpectedOrigins(requestContext);
    if (expectedOrigins.isEmpty()) {
      // We somehow managed to get here without a StrictReferer annotation at all!
      return;
    }
    String origin = requestContext.getHeaderString(ORIGIN_HEADER);
    final boolean valid;
    if (origin != null) {
      valid = expectedOrigins.contains(origin);

      if (!valid) {
        auditLogService.event(StrictRefererErrorLogEvent.class)
            .setEndpoint(requestContext.getUriInfo().getPath())
            .setActualOrigin(origin)
            .setExpectedOrigins(expectedOrigins)
            .setFrom(StrictRefererErrorLogEvent.OriginFrom.ORIGIN)
            .log();
      }
    } else {
      String referer = requestContext.getHeaderString(REFERER_HEADER);
      if (referer != null) {
        String originFromReferer = OriginHelper.originFromUri(referer);
        valid = expectedOrigins.contains(originFromReferer);

        if (!valid) {
          auditLogService.event(StrictRefererErrorLogEvent.class)
              .setEndpoint(requestContext.getUriInfo().getPath())
              .setActualOrigin(originFromReferer)
              .setExpectedOrigins(expectedOrigins)
              .setFrom(StrictRefererErrorLogEvent.OriginFrom.REFERER)
              .log();
        }
      } else {
        valid = false;
        auditLogService.event(StrictRefererErrorLogEvent.class)
            .setEndpoint(requestContext.getUriInfo().getPath())
            .setExpectedOrigins(expectedOrigins)
            .log();
      }
    }
    if (!valid) {
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
    }
  }

  private ImmutableSet<String> getExpectedOrigins(ContainerRequestContext requestContext) {
    String expectedOrigin = OriginHelper.originFromUri(requestContext.getUriInfo().getRequestUri().toString());

    StrictReferer strictReferer = resourceInfo.getResourceMethod().getAnnotation(StrictReferer.class);
    if (strictReferer == null) {
      strictReferer = resourceInfo.getResourceClass().getAnnotation(StrictReferer.class);
      if (strictReferer == null) {
        return ImmutableSet.of();
      }
    }
    if (strictReferer.allowPortal() && settings.portalOrigin != null) {
      return ImmutableSet.of(expectedOrigin, settings.portalOrigin);
    } else {
      return ImmutableSet.of(expectedOrigin);
    }
  }

  public static class StrictRefererErrorLogEvent extends AuditLogEvent {
    public StrictRefererErrorLogEvent() {
      super("strict_referer_event");
    }

    public StrictRefererErrorLogEvent setActualOrigin(String actualOrigin) {
      this.addContextData("actual_origin", actualOrigin);
      return this;
    }

    public StrictRefererErrorLogEvent setExpectedOrigins(ImmutableSet<String> expectedOrigins) {
      this.addContextData("expected_origins", expectedOrigins);
      return this;
    }

    public StrictRefererErrorLogEvent setEndpoint(String endpoint) {
      this.addContextData("endpoint", endpoint);
      return this;
    }

    public StrictRefererErrorLogEvent setFrom(OriginFrom from) {
      this.addContextData("from", from);
      return this;
    }

    public static enum OriginFrom {
      ORIGIN, REFERER
    }
  }
}
