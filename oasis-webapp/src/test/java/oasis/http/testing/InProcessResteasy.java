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
package oasis.http.testing;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;

import oasis.http.HttpServer;
import oasis.web.providers.JacksonJsonProvider;
import oasis.web.security.SecureFilter;

/**
 * Creates an in-process Resteasy container and client. Depends on Jukito.
 */
public class InProcessResteasy extends net.ltgt.resteasy.testing.InProcessResteasy {

  private final Injector injector;

  @Inject
  InProcessResteasy(Injector injector) {
    // Set the scheme do HTTPS to match what the SecureFilter (added in configureDeployment) will do.
    super(URI.create("https://localhost/"));
    this.injector = injector;
  }

  @Override
  protected void configureDeployment(ResteasyDeployment deployment) {
    ResteasyProviderFactory providerFactory = HttpServer.createResteasyProviderFactory(injector);
    providerFactory.register(JacksonJsonProvider.class); // Note: this is our own implementation

    // Add SecureFilter to detect Resteasy bugs earlier.
    // Works in conjunction with the ClientRequestFilter added in configureClient
    providerFactory.register(SecureFilter.class);

    deployment.setProviderFactory(providerFactory);
  }

  @Override
  protected void configureClient(ResteasyClientBuilder builder) {
    builder.register(JacksonJsonProvider.class); // Note: this is our own implementation

    // Simulate an SSL terminator in front of the application.
    // Works in conjunction with the SecureFilter added in configureDeployment
    builder.register(new ClientRequestFilter() {
      @Override
      public void filter(ClientRequestContext requestContext) throws IOException {
        requestContext.getHeaders().putSingle("X-Forwarded-Proto", "https");
      }
    });
  }
}
