package oasis.web;

import java.io.IOException;

import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;

public class WebApp {

  public static void main(String[] args) throws IOException {
    try {
      final NettyJaxrsServer server = new NettyJaxrsServer();
      server.getDeployment().setApplication(new Application());
      // port defaults to 8080

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
