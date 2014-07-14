package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.jongo.applications.JongoScope;
import oasis.jongo.applications.v2.JongoAppInstance;
import oasis.jongo.applications.v2.JongoApplication;
import oasis.jongo.applications.v2.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ScopeCardinality;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Scope;
import oasis.model.i18n.LocalizableString;
import oasis.openidconnect.OpenIdConnectModule;

@SuppressWarnings("deprecation")
public class MigrateApplicationsV2 extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new MigrateApplicationsV2().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject oasis.jongo.JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(MigrateApplicationsV2.class);
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
      migrateApplications();
      migrateScopes();
    } finally {
      jongoService.stop();
    }
  }

  private void migrateApplications() {
    logger().info("Migrating applications...");
    if (dryRun) {
      for (oasis.jongo.applications.JongoApplication application : jongoProvider.get().getCollection("applications")
          .find("{ $or: [ { applicationType: { $exists: 1 } }, { serviceProvider : { $exists: 1 } }, { dataProviders: { $exists: 1, $size: 0 } } ] }")
          .as(oasis.jongo.applications.JongoApplication.class)) {
        migrateApplication(application);
      }
    } else {
      oasis.jongo.applications.JongoApplication application;
      do {
        application = jongoProvider.get().getCollection("applications")
            .findAndModify(
                "{ $or: [ { applicationType: { $exists: 1 } }, { serviceProvider : { $exists: 1 } }, { dataProviders: { $exists: 1, $size: 0 } } ] }")
            .remove()
            .as(oasis.jongo.applications.JongoApplication.class);
        if (application != null) {
          migrateApplication(application);
        }
      } while (application != null);
    }
  }

  private void migrateApplication(oasis.jongo.applications.JongoApplication application) {
    if (application.getApplicationType() == oasis.model.applications.Application.ApplicationType.CLASS) {
      logger().warn("Ignoring application with type=CLASS: {}", application.getId());
      return;
    }
    logger().info("Processing application {}", application.getId());
    if (application.getServiceProvider() == null && application.getDataProviders().isEmpty()) {
      logger().warn("  Ignoring application; no service provider and no data provider");
      return;
    }
    // Application
    logger().info("  Creating application...");
    JongoApplication newApplication = new JongoApplication();
    if (!dryRun) {
      newApplication.setName(application.getName());
      newApplication.setIcon(new LocalizableString(application.getIconUri()));
      newApplication.setProvider_id(application.getInstanceAdmin());
      newApplication.setVisible(false); // Default all applications to be "singletons"
      newApplication.setModified(System.currentTimeMillis());
      jongoProvider.get().getCollection("applications").insert(newApplication);
      logger().info("    Created application {}", newApplication.getId());
    }
    if (application.getServiceProvider() != null) {
      createAppInstanceFromServiceProvider(newApplication.getId(), application);
    }
    for (DataProvider dataProvider : application.getDataProviders()) {
      createAppInstanceFromDataProvider(newApplication.getId(), application, dataProvider);
    }
  }

  private void createAppInstanceFromServiceProvider(String applicationId, oasis.jongo.applications.JongoApplication application) {
    // AppInstance
    logger().info("  Creating application instance {} from service provider", application.getServiceProvider().getId());
    JongoAppInstance appInstance = new JongoAppInstance();
    if (!dryRun) {
      appInstance.setId(application.getServiceProvider().getId()); // keep the client_id!
      appInstance.setName(application.getName());
      appInstance.setIcon(new LocalizableString(application.getIconUri()));
      appInstance.setProvider_id(application.getInstanceAdmin());
      appInstance.setStatus(AppInstance.InstantiationStatus.RUNNING);
      appInstance.setApplication_id(applicationId);
      appInstance.setNeeded_scopes(FluentIterable.from(application.getServiceProvider().getScopeCardinalities())
          .transform(new Function<ScopeCardinality, AppInstance.NeededScope>() {
            @Override
            public AppInstance.NeededScope apply(ScopeCardinality input) {
              AppInstance.NeededScope neededScope = new AppInstance.NeededScope();
              neededScope.setScope_id(input.getScopeId());
              neededScope.setMotivation(input.getMotivations());
              return neededScope;
            }
          })
          .toSet());
      appInstance.setModified(System.currentTimeMillis());
      jongoProvider.get().getCollection("app_instances").insert(appInstance);
    }
    // Service
    logger().info("  Creating service...");
    if (!dryRun) {
      JongoService service = new JongoService();
      service.setInstance_id(appInstance.getId());
      service.setName(application.getServiceProvider().getName());
      service.setProvider_id(application.getInstanceAdmin());
      service.setRedirect_uris(Sets.newLinkedHashSet(application.getServiceProvider().getRedirect_uris()));
      service.setPost_logout_redirect_uris(Sets.newLinkedHashSet(application.getServiceProvider().getPost_logout_redirect_uris()));
      service.setModified(System.currentTimeMillis());
      jongoProvider.get().getCollection("services").insert(service);
      logger().info("    Created service {}", service.getId());
    }
  }

  private void createAppInstanceFromDataProvider(String applicationId, oasis.jongo.applications.JongoApplication application,
      DataProvider dataProvider) {
    // AppInstance
    logger().info("  Creating application instance {} from data provider", dataProvider.getId());
    JongoAppInstance appInstance = new JongoAppInstance();
    if (!dryRun) {
      appInstance.setId(dataProvider.getId()); // keep the client_id!
      appInstance.setName(application.getName());
      appInstance.setIcon(new LocalizableString(application.getIconUri()));
      appInstance.setProvider_id(application.getInstanceAdmin());
      appInstance.setStatus(AppInstance.InstantiationStatus.RUNNING);
      appInstance.setApplication_id(applicationId);
      appInstance.setModified(System.currentTimeMillis());
      jongoProvider.get().getCollection("app_instances").insert(appInstance);
    }
    // Scopes
    logger().info("  Processing scopes...");
    for (String scopeId : dataProvider.getScopeIds()) {
      logger().info("    Migrating scope {}", scopeId);
      if (!dryRun) {
        JongoScope oldScope = jongoProvider.get().getCollection("scopes")
            .findAndModify("{ id: # }", scopeId)
            .remove()
            .as(JongoScope.class);
        if (oldScope == null) {
          logger().warn("      No scope {} found in scopes collection; skipping", scopeId);
          continue;
        }
        Scope scope = new Scope();
        scope.setInstance_id(appInstance.getId());
        scope.setLocal_id(scopeId);
        scope.setId(scopeId); // For backwards compatibility, don't rename scopes with the instance_id prefix
        scope.setName(oldScope.getTitle());
        scope.setDescription(oldScope.getDescription());
        jongoProvider.get().getCollection("scopes").insert(scope);
      }
    }
  }

  private void migrateScopes() {
    logger().info("Migrating scopes...");
    if (dryRun) {
      logger().warn("Running in dry-run mode; this step will probably process too much scopes (not processed when migrating applications)");
      for (JongoScope oldScope : jongoProvider.get().getCollection("scopes").find("{ title: { $exists: 1 }, local_id: { $exists: 0 } }").as(JongoScope.class)) {
        migrateScope(oldScope);
      }
    } else {
      JongoScope oldScope;
      do {
        oldScope = jongoProvider.get()
            .getCollection("scopes")
            .findAndModify("{ title: { $exists: 1 }, local_id: { $exists: 0 } }")
            .remove()
            .as(JongoScope.class);
        if (oldScope != null) {
          migrateScope(oldScope);
        }
      } while (oldScope != null);
    }
  }

  private void migrateScope(JongoScope oldScope) {
    logger().info("  Migrating scope {}", oldScope.getId());
    if (!dryRun) {
      Scope scope = new Scope();
      scope.setLocal_id(oldScope.getId());
      scope.setInstance_id(oldScope.getDataProviderId());
      scope.setId(oldScope.getId()); // For backwards compatibility, don't rename scopes with the instance_id prefix
      scope.setName(oldScope.getTitle());
      scope.setDescription(oldScope.getDescription());
      jongoProvider.get().getCollection("scopes").insert(scope);
    }
  }
}
