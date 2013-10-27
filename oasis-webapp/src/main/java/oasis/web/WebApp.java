package oasis.web;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;

import oasis.web.guice.GuiceInjectorFactory;
import oasis.web.guice.OasisGuiceModule;
import oasis.web.providers.NewCookieHeaderDelegate;

public class WebApp {

  private static final class CmdLineArgs {
    @Option(name = "-c", usage = "Configuration file", metaVar = "file")
    public File configurationFile;

    @Option(name = "-l", usage = "Log4j configuration file", metaVar = "file")
    public File log4jConfig;
  }

  public static void main(String[] args) throws Throwable {
    CmdLineArgs a = parseArgs(args);

    final NettyJaxrsServer server = new NettyJaxrsServer();
    server.getDeployment().setApplication(new Application());
    //server.setPort(8080); // TODO: get port from settings

    // Guice configuration
    final Injector injector = Guice.createInjector(new OasisGuiceModule());
    ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
    providerFactory.setInjectorFactory(new GuiceInjectorFactory(injector));
    server.getDeployment().setProviderFactory(providerFactory);
    // workaround for https://java.net/jira/browse/JAX_RS_SPEC-430
    providerFactory.addHeaderDelegate(NewCookie.class, new NewCookieHeaderDelegate());

    // Swagger configuration
    Properties swaggerProps = new Properties();
    try (InputStream in = Resources.getResource("swagger.properties").openStream()) {
      swaggerProps.load(in);
    }
    ConfigFactory.config().setApiVersion(swaggerProps.getProperty("api.version"));
    // TODO: authorizations and info
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.err.print("stopping");
        server.stop();
      }
    });

    server.start();
    System.out.println(String.format("JAX-RS app started on port %d;", server.getPort()));
  }

  private static CmdLineArgs parseArgs(String[] args) {
    CmdLineArgs result = new CmdLineArgs();
    CmdLineParser parser = new CmdLineParser(result);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      printUsage(e);
      System.exit(1);
    }

    return result;
  }

  private static void printUsage(CmdLineException e) {
    // TODO: detailed usage description
    System.err.println(e.getMessage());
    e.getParser().printUsage(System.err);
  }

  private WebApp() { /* non instantiable */ }
}
