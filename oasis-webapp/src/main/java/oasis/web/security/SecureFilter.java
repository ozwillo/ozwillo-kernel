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
package oasis.web.security;

import java.io.IOException;
import java.security.Principal;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 * Hack into Resteasy to set the {@link UriInfo} to HTTPS and {@link SecurityContext#isSecure()} to true
 * in the presence of a {@code X-Forwarded-Proto: https} HTTP request header.
 * <p>
 * <strong>NOTE:</strong> this is highly insecure in absence of a reverse proxy enforcing the value (or absence) of
 * the header.
 */
// TODO: possibly make configurable (à la Play's trustxforward) when we know the details of the final deployment infra
@PreMatching
public class SecureFilter implements ContainerRequestFilter {
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if ("https".equalsIgnoreCase(requestContext.getHeaderString("X-Forwarded-Proto"))) {
      requestContext.setRequestUri(
          requestContext.getUriInfo().getBaseUriBuilder().scheme("https").build(),
          requestContext.getUriInfo().getRequestUriBuilder().scheme("https").build());

      final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
      requestContext.setSecurityContext(new SecurityContext() {
        @Override
        public Principal getUserPrincipal() {
          return oldSecurityContext.getUserPrincipal();
        }

        @Override
        public boolean isUserInRole(String role) {
          return oldSecurityContext.isUserInRole(role);
        }

        @Override
        public boolean isSecure() {
          return true;
        }

        @Override
        public String getAuthenticationScheme() {
          return oldSecurityContext.getAuthenticationScheme();
        }
      });
    }
  }
}
