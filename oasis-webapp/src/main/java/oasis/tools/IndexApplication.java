package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.jest.JestService;
import oasis.jest.applications.v2.JestCatalogEntryRepository;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.soy.SoyGuiceModule;

public class IndexApplication extends CommandLineTool {
  public static void main(String[] args) throws Exception {
    new IndexApplication().run(args);
  }

  @Argument(required = true)
  private String applicationId;

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Inject JongoService jongoService;
  @Inject JestService jestService;
  @Inject Provider<ApplicationRepository> applicationRepositoryProvider;
  @Inject Provider<JestCatalogEntryRepository> jestCatalogEntryRepositoryProvider;

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        new SoyGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        ElasticsearchModule.create(config.getConfig("oasis.elasticsearch")),
        new JestModule(),
        new HttpClientModule(),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    jestService.start();

    try {
      Application application = applicationRepositoryProvider.get().getApplication(applicationId);

      if (application != null && application.isVisible()) {
        logger().info("Indexing application {} ...", applicationId);

        boolean success;
        try {
          if (!dryRun) {
            jestCatalogEntryRepositoryProvider.get().asyncIndex(application).get();
          }
          success = true;
        } catch (Exception e) {
          logger().error("Error when indexing application {}", applicationId);
          success = false;
        }
        if (success) {
          logger().info("Application {} successfully indexed", applicationId);
        }
      } else {
        logger().info("Removing application {} from the index ...", applicationId);

        boolean success;
        try {
          if (!dryRun) {
            jestCatalogEntryRepositoryProvider.get()
                .asyncDelete(applicationId, CatalogEntry.EntryType.APPLICATION)
                .get();
          }
          success = true;
        } catch (Exception e) {
          logger().error("Error when removing application {} from the index", applicationId);
          success = false;
        }
        if (success) {
          logger().info("Application {} successfully removed from the index", applicationId);
        }
      }
    } finally {
      jongoService.stop();
      jestService.stop();
    }
  }

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(IndexApplication.class);
  }
}
