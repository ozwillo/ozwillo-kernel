package oasis.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;

import oasis.audit.log4j.logstash.LogstashLog4JAuditModule;
import oasis.audit.noop.NoopAuditModule;
import oasis.http.HttpServer;
import oasis.http.HttpServerModule;
import oasis.storage.JongoModule;
import oasis.storage.JongoService;
import oasis.web.guice.OasisGuiceModule;
import oasis.web.kibana.KibanaModule;

public class WebApp {
  // logger is not a static field to be initialized once log4j is configured
  private static Logger logger() {
    return LoggerFactory.getLogger(WebApp.class);
  }

  private static final class CmdLineArgs {
    @Option(name = "-c", usage = "Configuration file", metaVar = "file")
    public Path configurationPath;

    @Option(name = "-l", usage = "Log4j configuration file", metaVar = "file")
    public Path log4jConfig;
  }

  public static void main(String[] args) throws Throwable {
    CmdLineArgs a = parseArgs(args);

    if (a.log4jConfig != null && Files.isRegularFile(a.log4jConfig) && Files.isReadable(a.log4jConfig)) {
      System.setProperty("log4j.configurationFile", a.log4jConfig.toRealPath().toString());
    } else {
      // use default log4j configuration: all INFO and more to stdout
      System.setProperty("org.apache.logging.log4j.level", "INFO");

      if (a.log4jConfig != null) {
        if (!Files.isRegularFile(a.log4jConfig) || !Files.isReadable(a.log4jConfig)) {
          logger().warn("log4j2 configuration file not found or not readable. Using default configuration.");
        }
      } else {
        logger().debug("No log4j2 configuration file specified. Using default configuration.");
      }
    }

    final Config config = SettingsLoader.load(a.configurationPath);

    AbstractModule auditModule = (config.getBoolean("oasis.audit.disabled")) ?
        new NoopAuditModule() :
        LogstashLog4JAuditModule.create(config.withOnlyPath("oasis.audit.logstash"));

    final Injector injector = Guice.createInjector(
        new OasisGuiceModule(),
        JongoModule.create(config.withOnlyPath("oasis.mongo")),
        auditModule,
        HttpServerModule.create(config.withOnlyPath("oasis.http")),
        KibanaModule.create(config.withOnlyPath("oasis.kibana"))
    );

    final HttpServer server = injector.getInstance(HttpServer.class);
    final JongoService jongo = injector.getInstance(JongoService.class);

    initSwagger(config);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.stop();
        jongo.stop();
      }
    });

    jongo.start();
    server.start();
  }

  private static void initSwagger(Config config) {
    ConfigFactory.config().setApiVersion(config.getString("swagger.api.version"));

    // TODO: authorizations and info
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());
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
