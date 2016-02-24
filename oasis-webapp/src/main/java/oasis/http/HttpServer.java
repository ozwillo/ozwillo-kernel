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
package oasis.http;

import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;

import oasis.web.Application;
import oasis.web.guice.GuiceInjectorFactory;
import oasis.http.fixes.NewCookieHeaderDelegate;

public class HttpServer {
  private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

  public static ResteasyProviderFactory createResteasyProviderFactory(Injector injector) {
    ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
    providerFactory.setInjectorFactory(new GuiceInjectorFactory(injector));
    // workaround for https://java.net/jira/browse/JAX_RS_SPEC-430
    providerFactory.addHeaderDelegate(NewCookie.class, new NewCookieHeaderDelegate());
    return providerFactory;
  }

  private final Injector injector;

  private final HttpServerModule.Settings settings;

  private NettyJaxrsServer server;

  @Inject
  HttpServer(Injector injector, HttpServerModule.Settings settings) {
    this.injector = injector;
    this.settings = settings;
  }

  public void start() {
    server = new NettyJaxrsServer();
    server.getDeployment().setApplication(new Application());
    server.setPort(settings.nettyPort);

    ResteasyProviderFactory providerFactory = createResteasyProviderFactory(injector);
    server.getDeployment().setProviderFactory(providerFactory);

    server.start();
    logger.info("Oasis server started on port {};", server.getPort());
  }

  public void stop() {
    server.stop();
    logger.info("Oasis server stopped.");
  }
}
