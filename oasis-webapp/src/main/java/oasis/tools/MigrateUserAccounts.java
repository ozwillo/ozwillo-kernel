package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.directory.JongoOrganizationMembership;
import oasis.jongo.guice.JongoModule;
import oasis.openidconnect.OpenIdConnectModule;

public class MigrateUserAccounts extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateUserAccounts().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateUserAccounts.class);
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
      if (dryRun) {
        for (Agent agent : jongoProvider.get().getCollection("account").find("{ organizationId: { $exists: 1 } }").as(Agent.class)) {
          migrateAgent(agent);
        }
      } else {
        Agent agent;
        do {
          agent = jongoProvider.get()
              .getCollection("account")
              .findAndModify("{ organizationId: { $exists: 1 } }")
              .with("{ $unset: { organizationId: 1, admin: 1 } }")
              .as(Agent.class);
          if (agent != null) {
            migrateAgent(agent);
          }
        } while (agent != null);
      }
      logger().info("Updating all agent and citizen accounts to generic user account");
      if (!dryRun) {
        int n =  jongoProvider.get().getCollection("account")
            .update("{ type: { $in: [ \".CitizenAccount\", \".AgentAccount\" ] } }")
            .multi()
            .with("{ $set: { type: \".UserAccount\" } }")
            .getN();
        logger().info("  Updated {} accounts.", n);
      }
    } finally {
      jongoService.stop();
    }
  }

  private void migrateAgent(Agent agent) {
    logger().info("Migrating agent {}", agent.id);
    if (!dryRun) {
      JongoOrganizationMembership member = new JongoOrganizationMembership();
      member.setAccountId(agent.id);
      member.setOrganizationId(agent.organizationId);
      member.setAdmin(agent.admin);
      jongoProvider.get().getCollection("organization_members").insert(member);
      logger().info("  Created organization member {}", member.getId());
    }
  }

  static class Agent {
    @JsonProperty String id;
    @JsonProperty String organizationId;
    @JsonProperty boolean admin;
  }
}
