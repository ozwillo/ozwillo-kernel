package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.accounts.Account;
import oasis.model.accounts.AgentAccount;
import oasis.model.accounts.CitizenAccount;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.UserPasswordAuthenticator;

public class MigrateUserPasswords extends CommandLineTool {

  public static void main(String[] args) throws Throwable {
    new MigrateUserPasswords().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;
  @Inject Provider<UserPasswordAuthenticator> authenticatorProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateUserPasswords.class);
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
      // 1. get all accounts
      for (Account account : jongoProvider.get().getCollection("account").find().as(Account.class)) {
        // 2. migrate credentials
        if (account instanceof CitizenAccount) {
          String email = ((CitizenAccount) account).getEmailAddress();
          String password = email.substring(0, email.indexOf('@'));
          migratePassword(account.getId(), password);
        } else if (account instanceof AgentAccount) {
          migratePassword(account.getId(), ((AgentAccount) account).getEmailAddress());
        } else {
          logger().warn("Found an Account that's neither a CitizenAccount not an AgentAccount: {} (class={})",
              account.getId(), account.getClass());
        }
      }
    } finally {
      jongoService.stop();
    }
  }

  private void migratePassword(String accountId, String password) {
    logger().info("Migrating password for account {} (password={})", accountId, password);
    if (!dryRun) {
      authenticatorProvider.get().setPassword(accountId, password);
    }
  }
}
