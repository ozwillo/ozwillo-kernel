package oasis.http.testing;

import java.net.URI;

import javax.ws.rs.client.Client;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.rules.ExternalResource;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import com.google.inject.Injector;

import oasis.http.HttpServer;
import oasis.web.providers.JacksonContextResolver;

/**
 * Creates an in-process Resteasy container and client. Depends on Jukito.
 */
public class InProcessResteasy extends ExternalResource {

  public static final URI BASE_URI = URI.create("http://localhost/");

  private final Injector injector;

  private ResteasyDeployment deployment;
  private Client client;

  @Inject
  InProcessResteasy(Injector injector) {
    this.injector = injector;
  }

  public ResteasyDeployment getDeployment() {
    return deployment;
  }

  public Client getClient() {
    return client;
  }

  @Override
  protected void before() throws Throwable {
    ResteasyProviderFactory providerFactory = HttpServer.createResteasyProviderFactory(injector);
    providerFactory.register(JacksonJsonProvider.class);
    providerFactory.register(JacksonContextResolver.class);

    deployment = new ResteasyDeployment();
    deployment.setProviderFactory(providerFactory);

    deployment.start();

    client = new ResteasyClientBuilder()
        .httpEngine(new InProcessClientHttpEngine(deployment.getDispatcher(), BASE_URI))
        .register(JacksonJsonProvider.class)
        .build();
  }

  @Override
  protected void after() {
    deployment.stop();
  }
}
