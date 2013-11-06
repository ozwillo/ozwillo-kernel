package oasis.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import oasis.web.guice.OasisGuiceModule;

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

    final Injector injector = Guice.createInjector(new OasisGuiceModule(a.configurationPath));

    final OasisServer server = injector.getInstance(OasisServer.class);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.stop();
      }
    });

    server.start();
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
