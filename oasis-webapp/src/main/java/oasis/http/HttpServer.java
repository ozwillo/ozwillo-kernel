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
import oasis.web.providers.NewCookieHeaderDelegate;

public class HttpServer {
  private final static Logger logger = LoggerFactory.getLogger(HttpServer.class);

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

    ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
    providerFactory.setInjectorFactory(new GuiceInjectorFactory(this.injector));
    server.getDeployment().setProviderFactory(providerFactory);
    // workaround for https://java.net/jira/browse/JAX_RS_SPEC-430
    providerFactory.addHeaderDelegate(NewCookie.class, new NewCookieHeaderDelegate());

    server.start();
    logger.info("Oasis server started on port {};", server.getPort());
  }

  public void stop() {
    server.stop();
    logger.info("Oasis server stopped.");
  }
}
