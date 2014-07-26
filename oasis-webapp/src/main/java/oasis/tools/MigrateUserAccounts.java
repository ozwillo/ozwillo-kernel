package oasis.tools;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.BaseEncoding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mongodb.CommandFailureException;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.directory.JongoOrganizationMembership;
import oasis.jongo.guice.JongoModule;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.openidconnect.OpenIdConnectModule;

public class MigrateUserAccounts extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateUserAccounts().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;
  @Inject Provider<CredentialsRepository> credentialsRepositoryProvider;
  @Inject Provider<AuthorizationRepository> authorizationRepositoryProvider;

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
      logger().info("Deleting all tokens (stored within accounts)");
      if (!dryRun) {
        jongoProvider.get().getCollection("account")
            .update("{ tokens: { $exists: 1 } }")
            .multi()
            .with("{ $unset: { tokens: 1 } }");
        logger().info("  Deleting associated index");
        try {
          jongoProvider.get().getCollection("account").dropIndex("{ tokens.id: 1 }");
        } catch (CommandFailureException cfe) { /* ignore */ }
      }
      logger().info("Migrating credentials to new collection");
      if (!dryRun) {
        Credentials credentials;
        do {
          credentials = jongoProvider.get()
              .getCollection("account")
              .findAndModify("{ password: { $exists: 1 } }")
              .with("{ $unset: { password: 1, passwordSalt: 1 } }")
              .as(Credentials.class);
          if (credentials != null) {
            credentialsRepositoryProvider.get().saveCredentials(ClientType.USER, credentials.id,
                BaseEncoding.base64().decode(credentials.password),
                BaseEncoding.base64().decode(credentials.passwordSalt));
          }
        } while (credentials != null);
      }
      logger().info("Migrating authorized scopes to new collection");
      if (!dryRun) {
        AccountWithAuthorizedScopes accountWithAuthorizedScopes;
        do {
          accountWithAuthorizedScopes = jongoProvider.get()
              .getCollection("account")
              .findAndModify("{ authorizedScopes: { $exists: 1 } }")
              .with("{ $unset: { authorizedScopes: 1 } }")
              .as(AccountWithAuthorizedScopes.class);
          if (accountWithAuthorizedScopes != null && accountWithAuthorizedScopes.authorizedScopes != null) {
            for (AuthorizedScopes authorizedScopes : accountWithAuthorizedScopes.authorizedScopes) {
              authorizationRepositoryProvider.get().authorize(
                  accountWithAuthorizedScopes.id,
                  authorizedScopes.serviceProviderId,
                  authorizedScopes.scopeIds);
            }
          }
        } while (accountWithAuthorizedScopes != null);
        logger().info("  Deleting associated index");
        try {
          jongoProvider.get().getCollection("account").dropIndex("{ id: 1, authorizedScopes.serviceProviderId: 1 }");
        } catch (CommandFailureException cfe) { /* ignore */ }
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

  static class Credentials {
    @JsonProperty String id;
    @JsonProperty String password;
    @JsonProperty String passwordSalt;
  }

  static class AccountWithAuthorizedScopes {
    @JsonProperty String id;
    @JsonProperty List<AuthorizedScopes> authorizedScopes;
  }

  static class AuthorizedScopes {
    @JsonProperty String serviceProviderId;
    @JsonProperty Set<String> scopeIds;
  }
}
