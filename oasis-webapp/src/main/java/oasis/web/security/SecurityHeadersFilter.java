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
package oasis.web.security;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    if (isRedirect(responseContext) || isHTML(responseContext)) {
      responseContext.getHeaders().putSingle("Content-Security-Policy", "" +
              "default-src 'none'; " +
              "script-src 'self' 'unsafe-inline'; " +
              "style-src 'unsafe-inline'; " +
              "img-src 'self'; " +
              "font-src 'self'; " +
              "form-action 'self'; " +
              "disown-opener; " +
              "reflected-xss block; " +
              "referrer origin-when-cross-origin; " +
              "require-sri-for script;");
    }
  }

  private boolean isRedirect(ContainerResponseContext responseContext) {
    return responseContext.getStatusInfo().getFamily() == Response.Status.Family.REDIRECTION;
  }

  private boolean isHTML(ContainerResponseContext responseContext) {
    return MediaType.TEXT_HTML_TYPE.isCompatible(responseContext.getMediaType());
  }
}
