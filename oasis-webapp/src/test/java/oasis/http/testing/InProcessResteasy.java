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

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;

import oasis.http.HttpServer;
import oasis.web.providers.JacksonJsonProvider;

/**
 * Creates an in-process Resteasy container and client. Depends on Jukito.
 */
public class InProcessResteasy extends net.ltgt.resteasy.testing.InProcessResteasy {

  private final Injector injector;

  @Inject
  InProcessResteasy(Injector injector) {
    this.injector = injector;
  }

  @Override
  protected void configureDeployment(ResteasyDeployment deployment) {
    ResteasyProviderFactory providerFactory = HttpServer.createResteasyProviderFactory(injector);
    providerFactory.register(JacksonJsonProvider.class); // Note: this is our own implementation

    deployment.setProviderFactory(providerFactory);
  }

  @Override
  protected void configureClient(ResteasyClientBuilder builder) {
    builder.register(JacksonJsonProvider.class); // Note: this is our own implementation
  }
}
