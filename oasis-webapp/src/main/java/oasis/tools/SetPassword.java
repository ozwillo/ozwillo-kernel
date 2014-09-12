package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.CredentialsService;
import oasis.services.authn.PasswordGenerator;

public class SetPassword extends CommandLineTool {

  public static void main(String[] args) throws Throwable {
    new SetPassword().run(args);
  }

  @Option(name = "-t", aliases = { "-ct", "--type", "--client-type" }, required = true)
  private ClientType clientType;

  @Option(name = "-i", aliases = { "-id", "--id", "-u", "--user", "--client-id" }, required = true)
  private String id;

  @Option(name = "-p", aliases = { "-pw", "-pwd", "--password", "--secret", "--client-secret" })
  private String pwd;

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<CredentialsRepository> credentialsRepositoryProvider;
  @Inject Provider<CredentialsService> credentialsServiceProvider;
  @Inject Provider<PasswordGenerator> passwordGeneratorProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(SetPassword.class);
  }

  public void run(String[] args) throws Throwable {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo")),
        // TODO: refactor to use a single subtree of the config
        OpenIdConnectModule.create(config.withOnlyPath("oasis.openid-connect")
            .withFallback(config.withOnlyPath("oasis.oauth"))
            .withFallback(config.withOnlyPath("oasis.session"))
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      if (credentialsRepositoryProvider.get().getCredentials(clientType, id) == null) {
        logger().error("No existing credentials for type={}, id={}", clientType, id);
        return;
      }

      if (Strings.isNullOrEmpty(pwd)) {
        logger().info("Generating password...");
        pwd = passwordGeneratorProvider.get().generate();
        logger().info("Generated password: {}", pwd);
      }

      if (!dryRun) {
        credentialsServiceProvider.get().setPassword(clientType, id, pwd);
      }
    } finally {
      jongoService.stop();
    }
  }
}
