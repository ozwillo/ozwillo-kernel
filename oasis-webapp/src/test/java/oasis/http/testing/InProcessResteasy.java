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

import java.net.URI;
import java.security.Principal;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.SecurityContext;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.rules.ExternalResource;

import com.google.inject.Inject;
import com.google.inject.Injector;

import oasis.http.HttpServer;
import oasis.web.providers.JacksonJsonProvider;

/**
 * Creates an in-process Resteasy container and client. Depends on Jukito.
 */
public class InProcessResteasy extends ExternalResource {

  public static final URI BASE_URI = URI.create("http://localhost/");

  private final Injector injector;

  private ResteasyDeployment deployment;
  private Client client;

  @Inject
  InProcessResteasy(Injector injector) {
    this.injector = injector;
  }

  public ResteasyDeployment getDeployment() {
    return deployment;
  }

  public Client getClient() {
    return client;
  }

  @Override
  protected void before() throws Throwable {
    ResteasyProviderFactory providerFactory = HttpServer.createResteasyProviderFactory(injector);
    providerFactory.register(JacksonJsonProvider.class); // Note: this is our own implementation

    deployment = new ResteasyDeployment();
    deployment.setProviderFactory(providerFactory);
    deployment.getDefaultContextObjects().put(SecurityContext.class, new DummySecurityContext());

    deployment.start();

    client = new ResteasyClientBuilder()
        .httpEngine(new InProcessClientHttpEngine(deployment.getDispatcher(), BASE_URI))
        .register(JacksonJsonProvider.class) // Note: this is our own implementation
        .build();
  }

  @Override
  protected void after() {
    deployment.stop();
  }

  private static class DummySecurityContext implements SecurityContext {
    @Override
    public Principal getUserPrincipal() {
      return null;
    }

    @Override
    public boolean isUserInRole(String role) {
      return false;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public String getAuthenticationScheme() {
      return null;
    }
  }
}
