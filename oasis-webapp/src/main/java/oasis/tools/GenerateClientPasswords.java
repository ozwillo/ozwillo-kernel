package oasis.tools;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.authn.ClientType;
import oasis.services.authn.CredentialsService;

public class GenerateClientPasswords extends CommandLineTool {

  public static void main(String[] args) throws Throwable {
    new GenerateClientPasswords().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;
  @Inject Provider<CredentialsService> credentialsServiceProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(GenerateClientPasswords.class);
  }

  public void run(String[] args) throws Throwable {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo"))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      // Create credentials for each data providers and service providers in credentials collection
      List<String> dataProviderIds = jongoProvider.get().getCollection("applications").distinct("dataProviders.id").as(String.class);
      List<String> serviceProviderIds = jongoProvider.get().getCollection("applications").distinct("serviceProvider.id").as(String.class);

      for (String providerId : Iterables.concat(dataProviderIds, serviceProviderIds)) {
        createCredentials(providerId, providerId);
      }
    } finally {
      jongoService.stop();
    }
  }

  private void createCredentials(String providerId, String password) {
    logger().info("Initializing password for provider {} (password={})", providerId, password);
    if (!dryRun) {
      credentialsServiceProvider.get().setPassword(ClientType.PROVIDER, providerId, password);
    }
  }
}
