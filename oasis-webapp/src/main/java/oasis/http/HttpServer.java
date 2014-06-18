package oasis.http;

import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;

import oasis.http.fixes.UnhandledExceptionMapper;
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
    // workaround for https://issues.jboss.org/browse/RESTEASY-1006
    providerFactory.register(UnhandledExceptionMapper.class);

    server.start();
    logger.info("Oasis server started on port {};", server.getPort());
  }

  public void stop() {
    server.stop();
    logger.info("Oasis server stopped.");
  }
}
