package oasis.tools;

import java.util.ArrayList;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.openidconnect.OpenIdConnectModule;

public class DeleteAppInstance extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new DeleteAppInstance().run(args);
  }

  @Option(name = "--instance", aliases = { "--instance-id", "--instance_id" },
      usage = "Instance ID")
  private String instance_id;

  @Option(name = "--creator", aliases = { "--creator-id", "--creator_id" },
      usage = "Creator (instantiator) ID")
  private String creator_id;

  @Option(name = "--org", aliases = { "--organization", "--organization-id", "--organization_id" },
      usage = "Organization ID")
  private String organization_id;

  @Option(name = "--application", aliases = { "--application-id", "--application_id" },
      usage = "Application ID")
  private String application_id;

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;
  @Inject Provider<AppInstanceRepository> appInstanceRepositoryProvider;
  @Inject Provider<AuthorizationRepository> authorizationRepositoryProvider;
  @Inject Provider<ServiceRepository> serviceRepositoryProvider;
  @Inject Provider<ScopeRepository> scopeRepositoryProvider;
  @Inject Provider<UserSubscriptionRepository> userSubscriptionRepositoryProvider;
  @Inject Provider<AccessControlRepository> accessControlRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(DeleteAppInstance.class);
  }

  public void run(String[] args) throws Exception {
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
      if (!Strings.isNullOrEmpty(instance_id)) {
        logger().info("Deleting one instance by ID");
        deleteInstance(instance_id);
      }
      if (!Strings.isNullOrEmpty(creator_id)) {
        logger().info("Deleting instances created by user: {}", creator_id);
        int n = deleteByCreatorId();
        logger().info("  Deleted {} instances.", n);
      }
      if (!Strings.isNullOrEmpty(organization_id)) {
        logger().info("Deleting instances bought for organization: {}", organization_id);
        int n = deleteByOrganizationId();
        logger().info("  Deleted {} instances.", n);
      }
      if (!Strings.isNullOrEmpty(application_id)) {
        logger().info("Deleting instances of application: {}", application_id);
        int n = deleteByApplicationId();
        logger().info("  Deleted {} instances.", n);
      }
    } finally {
      jongoService.stop();;
    }
  }

  private void deleteInstance(String instance_id) {
    // XXX: we first delete the instance, then all the orphan data: ACL, services, scopes, etc.
    logger().info("  Deleting instance {}...", instance_id);
    boolean silent = false;
    if (dryRun) {
      silent = appInstanceRepositoryProvider.get().getAppInstance(instance_id) == null;
    } else {
      silent = !appInstanceRepositoryProvider.get().deleteInstance(instance_id);
    }
    if (silent) {
      logger().warn("    No instance with ID existed; trying to delete other related data anyway.");
    }

    long authorizations;
    if (dryRun) {
      authorizations = jongoProvider.get().getCollection("authorized_scopes").count("{ client_id: # }", instance_id);
    } else {
      authorizations = authorizationRepositoryProvider.get().revokeAllForClient(instance_id);
    }
    logger().info("    Deleted {} authorizations from users for the instance", authorizations);

    ArrayList<String> scopes = new ArrayList<>();
    for (Scope scope : scopeRepositoryProvider.get().getScopesOfAppInstance(instance_id)) {
      scopes.add(scope.getId());
    }
    long revokedScopes;
    if (dryRun) {
      revokedScopes = jongoProvider.get().getCollection("authorized_scopes").count("{ scope_ids: { $in: # } }", scopes);
    } else {
      revokedScopes = authorizationRepositoryProvider.get().revokeForAllUsers(scopes);
    }
    logger().info("    Revoked {} authorizations from users to the instance scopes (for other instances)", revokedScopes);
    if (!dryRun) {
      scopeRepositoryProvider.get().deleteScopesOfAppInstance(instance_id);
    }
    logger().info("    Deleted {} scopes", scopes.size());

    int aces;
    if (dryRun) {
      aces = Iterables.size(accessControlRepositoryProvider.get().getAccessControlListForAppInstance(instance_id));
    } else {
      aces = accessControlRepositoryProvider.get().deleteAccessControlListForAppInstance(instance_id);
    }
    logger().info("    Deleted {} app_users", aces);

    ArrayList<String> serviceIds = new ArrayList<>();
    for (Service service : serviceRepositoryProvider.get().getServicesOfInstance(instance_id)) {
      serviceIds.add(service.getId());
    }
    long subscriptions;
    if (dryRun) {
      subscriptions = jongoProvider.get().getCollection("user_subscriptions").count("{ service_id: { $in: # } }", serviceIds);
    } else {
      subscriptions = userSubscriptionRepositoryProvider.get().deleteSubscriptionsForServices(serviceIds);
    }
    logger().info("    Deleted {} subscriptions for all services", subscriptions);

    if (!dryRun) {
      serviceRepositoryProvider.get().deleteServicesOfInstance(instance_id);
    }
    logger().info("    Deleted {} services", serviceIds.size());

    logger().info("    Instance {} deleted.", instance_id);
  }

  private int deleteByCreatorId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().findPersonalInstancesByUserId(creator_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }

  private int deleteByOrganizationId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().findByOrganizationId(organization_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }

  private int deleteByApplicationId() {
    int n = 0;
    for (AppInstance instance : appInstanceRepositoryProvider.get().getInstancesForApplication(application_id)) {
      deleteInstance(instance.getId());
      n++;
    }
    return n;
  }
}
