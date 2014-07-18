package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.joda.time.LocalDate;
import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.accounts.Address;
import oasis.openidconnect.OpenIdConnectModule;

public class MigrateIdentities extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateIdentities().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Option(name = "-nc", aliases = "--no-cleanup")
  private boolean noCleanup;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateIdentities.class);
  }

  private void run(String[] args) throws Exception {
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
      for (Account account : jongoProvider.get().getCollection("account").find("{ identityId: { $exists: 1 } }").as(Account.class)) {
        logger().info("Merging identity {} into account {}", account.identityId, account.id);
        if (!dryRun) {
          Identity identity = jongoProvider.get().getCollection("identity").findOne("{ id: # }", account.identityId).as(Identity.class);
          if (identity == null) {
            logger().warn("No identity found for account {} (identity ID: {})", account.id, account.identityId);
            continue;
          }
          identity.updatedAt = Math.max(account.modified, identity.updatedAt);
          jongoProvider.get().getCollection("account")
              .update("{ id: #, identityId: # }", account.id, account.identityId)
              .multi()
              .with("{ $set: #, $unset: { identityId: 1, modified: 1 } }", identity);
        }
      }
      if (!dryRun && !noCleanup) {
        logger().info("Dropping collection 'identity'");
        jongoProvider.get().getCollection("identity").drop();
      }
    } finally {
      jongoService.stop();
    }
  }

  static class Account {
    @JsonProperty String id;
    @JsonProperty String identityId;
    @JsonProperty long modified;
  }

  static class Identity {
    @JsonProperty String name;
    @JsonProperty String givenName;
    @JsonProperty String familyName;
    @JsonProperty String middleName;
    @JsonProperty String nickname;
    @JsonProperty String gender;
    @JsonProperty LocalDate birthdate;
    @JsonProperty String phoneNumber;
    @JsonProperty boolean phoneNumberVerified;
    @JsonProperty Address address;
    @JsonProperty long updatedAt;
  }
}
