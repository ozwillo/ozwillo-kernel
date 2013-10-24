package oasis.web.providers;

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

      // The new UriInfo after we setRequestUri
      ResteasyProviderFactory.getContextDataMap().put(UriInfo.class, requestContext.getUriInfo());

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
