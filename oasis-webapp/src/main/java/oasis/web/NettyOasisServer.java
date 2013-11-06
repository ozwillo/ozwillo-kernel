package oasis.web;

import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;

import oasis.web.guice.GuiceInjectorFactory;
import oasis.web.providers.NewCookieHeaderDelegate;

public class NettyOasisServer implements OasisServer {
  private final static Logger logger = LoggerFactory.getLogger(NettyOasisServer.class);

  private final Injector injector;

  private final Settings settings;

  private NettyJaxrsServer server;

  @Inject
  NettyOasisServer(Injector injector, Settings settings) {
    this.injector = injector;
    this.settings = settings;
  }

  @Override
  public void start() {
    server = new NettyJaxrsServer();
    server.getDeployment().setApplication(new Application());
    server.setPort(settings.nettyPort);

    ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
    providerFactory.setInjectorFactory(new GuiceInjectorFactory(this.injector));
    server.getDeployment().setProviderFactory(providerFactory);
    // workaround for https://java.net/jira/browse/JAX_RS_SPEC-430
    providerFactory.addHeaderDelegate(NewCookie.class, new NewCookieHeaderDelegate());

    // Swagger initialisation
    ConfigFactory.config().setApiVersion(settings.swaggerApiVersion);
    // TODO: authorizations and info
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());

    server.start();
    logger.info("Oasis server started on port {};", server.getPort());
  }

  @Override
  public void stop() {
    server.stop();
    logger.info("Oasis server stopped.");
  }
}
