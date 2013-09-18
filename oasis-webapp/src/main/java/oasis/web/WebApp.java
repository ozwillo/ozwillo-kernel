package oasis.web;

import com.google.common.io.Resources;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class WebApp {

  public static void main(String[] args) throws IOException {
    try {
      final NettyJaxrsServer server = new NettyJaxrsServer();
      server.getDeployment().setApplication(new Application());
      // port defaults to 8080

      // Swagger configuration
      Properties swaggerProps = new Properties();
      try (InputStream in = Resources.getResource("swagger.properties").openStream()) {
        swaggerProps.load(in);
      }
      ConfigFactory.setConfig(new SwaggerConfig(swaggerProps.getProperty("api.version"), swaggerProps.getProperty("swagger.version"), "", "", null, null));
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

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }
}
