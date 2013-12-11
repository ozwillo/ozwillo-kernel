package oasis.jongo.applications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.mongodb.WriteResult;

import oasis.model.InvalidVersionException;
import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ServiceProvider;

public class JongoApplicationRepository implements ApplicationRepository {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

  public static final String APPLICATION_PROJECTION = "{id:1, name:1, iconUri:1, modified:1}";
  public static final String DATA_PROVIDER_PROJECTION = "{ id:1, dataProviders: {$elemMatch: {id: #} } }";
  public static final String SERVICE_PROVIDER_PROJECTION = "{id:1, serviceProvider:1 }";

  private final Jongo jongo;

  @Inject
  JongoApplicationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Iterable<Application> getApplicationInstances(int start, int limit) {
    return (Iterable<Application>) (Iterable<?>) getApplications("{applicationType:'INSTANCE'}", start, limit);
  }

  @Override
  public Iterable<Application> getCatalogApplications(int start, int limit) {
    return (Iterable<Application>) (Iterable<?>) getApplications("{exposed:true}", start, limit);
  }

  @Override
  public Application getApplication(String appId) {
    JongoApplication app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection(APPLICATION_PROJECTION)
        .as(JongoApplication.class);

    if (app != null && app.isTenant()) {
      populateTenant(app);
    }
    return app;
  }

  @Override
  public Application createApplication(Application app) {
    JongoApplication jongoApplication = new JongoApplication(app);
    getApplicationsCollection().insert(jongoApplication);
    return jongoApplication;
  }

  @Override
  public Application instanciateApplication(String appId, String organizationId) {
    JongoApplication app = (JongoApplication) getApplication(appId);

    JongoApplication newApp = new JongoApplication(app);
    newApp.setParentApplicationId(appId);
    newApp.setInstanceAdmin(organizationId);
    newApp.setExposedInCatalog(false);
    if (Application.InstantiationType.COPY.equals(app.getInstantiationType())) {
      newApp.setDataProviders(app.getDataProviders());
      newApp.setServiceProvider(app.getServiceProvider());
    }

    getApplicationsCollection().insert(newApp);
    return newApp;
  }

  @Override
  public Application updateApplication(String appId, Application app, long[] versions) throws InvalidVersionException {

    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("modified:#");
    updateParameters.add(System.currentTimeMillis());

    if (app.getName() != null) {
      updateObject.append(",name:#");
      updateParameters.add(app.getName());
    }

    if (app.getIconUri() != null) {
      updateObject.append(",iconUri:#");
      updateParameters.add(app.getIconUri());
    }

    JongoApplication res = getApplicationsCollection()
        .findAndModify("{id: #, modified: { $in: # } }", appId, versions)
        .returnNew()
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .projection(APPLICATION_PROJECTION)
        .as(JongoApplication.class);

    if (res == null) {
      if (getApplicationsCollection().count("{ id: # }", appId) != 0) {
        throw new InvalidVersionException("application", appId);
      }
      logger.warn("More than one application with id: {}", appId);
    }
    return res;
  }

  @Override
  public boolean deleteApplication(String appId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getApplicationsCollection().remove("{id: #, modified: { $in: # } }", appId, versions);
    int n = wr.getN();
    if (n == 0) {
      if (getApplicationsCollection().count("{ id: # }", appId) != 0) {
        throw new InvalidVersionException("application", appId);
      }
      return false;
    }

    return true;
  }

  @Override
  public Iterable<DataProvider> getDataProviders(String appId) {
    JongoApplication app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection("{id:1, dataProviders:1, applicationType: 1, instanciationType: 1, parentApplicationId: 1 }")
        .as(JongoApplication.class);
    if (app == null) {
      return null;
    }
    if (app.isTenant()) {
      // TODO: "instantiate" scopes
      return getDataProviders(app.getParentApplicationId());
    }
    if (app.getDataProviders() == null) {
      return Collections.emptyList();
    }

    return (Iterable<DataProvider>) (Iterable<?>) app.getDataProviders();
  }

  @Override
  public DataProvider getDataProvider(String dataProviderId) {
    JongoApplication app = getApplicationsCollection()
        .findOne("{dataProviders.id: # }", dataProviderId)
        .projection(DATA_PROVIDER_PROJECTION, dataProviderId)
        .as(JongoApplication.class);
    if (app == null || app.getDataProviders().isEmpty()) {
      return null;
    }

    return app.getDataProviders().get(0);
  }

  @Override
  public DataProvider createDataProvider(String appId, DataProvider dataProvider) {

    JongoDataProvider jongoDataProvider = new JongoDataProvider(dataProvider);

    WriteResult wr = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$push:{dataProviders:#}}", jongoDataProvider);
    if (wr.getN() != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return jongoDataProvider;
  }

  @Override
  public DataProvider updateDataProvider(String dataProviderId, DataProvider dataProvider, long[] versions) throws InvalidVersionException {

    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("dataProviders.$.modified:#");
    updateParameters.add(System.currentTimeMillis());

    if (dataProvider.getName() != null) {
      updateObject.append(",dataProviders.$.name:#");
      updateParameters.add(dataProvider.getName());
    }

    if (dataProvider.getScopeIds() != null) {
      updateObject.append(",dataProviders.$.scopes:#");
      updateParameters.add(dataProvider.getScopeIds());
    }

    JongoApplication res = getApplicationsCollection()
        .findAndModify("{dataProviders: { $elemMatch: { id: #, modified: { $in: # } } } }", dataProviderId, versions)
        .returnNew()
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .projection(DATA_PROVIDER_PROJECTION, dataProviderId)
        .as(JongoApplication.class);

    if (res == null) {
      if (getApplicationsCollection().count("{ dataProviders.id: # }", dataProviderId) != 0) {
        throw new InvalidVersionException("dataProvider", dataProviderId);
      }
      logger.warn("More than one data provider with id: {}", dataProviderId);
      return null;
    }
    if (res.getDataProviders() == null || res.getDataProviders().isEmpty()) {
      return null;
    }

    return res.getDataProviders().get(0);
  }

  @Override
  public boolean deleteDataProvider(String dataProviderId, long[] versions) throws InvalidVersionException {

    WriteResult wr = getApplicationsCollection()
        .update("{ dataProviders: { $elemMatch: { id: #, modified: { $in: # } } } }", dataProviderId, versions)
        .with("{ $pull: { dataProviders: { id: #, modified: { $in: # } } } }", dataProviderId, versions);

    int n = wr.getN();
    if (n == 0) {
      if (getApplicationsCollection().count("{ dataProviders.id: # }", dataProviderId) != 0) {
        throw new InvalidVersionException("dataProvider", dataProviderId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} dataProviders with ID {}, that shouldn't have happened", n, dataProviderId);
    }
    return true;
  }

  @Override
  public ServiceProvider getServiceProviderFromApplication(String appId) {
    JongoApplication app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection("{id:1, serviceProvider:1, instanciationType: 1, applicationType: 1, parentApplicationId: 1 }")
        .as(JongoApplication.class);
    if (app == null) {
      return null;
    }
    if (app.isTenant()) {
      return getServiceProviderFromApplication(app.getParentApplicationId());
    }
    return app.getServiceProvider();
  }

  @Override
  public ServiceProvider getServiceProvider(String serviceProviderId) {
    JongoApplication app = getApplicationsCollection()
        .findOne("{serviceProvider.id: # }", serviceProviderId)
        .projection(SERVICE_PROVIDER_PROJECTION)
        .as(JongoApplication.class);
    if (app == null) {
      return null;
    }
    return app.getServiceProvider();
  }

  @Override
  public ServiceProvider createServiceProvider(String appId, ServiceProvider serviceProvider) {
    JongoServiceProvider jongoServiceProvider = new JongoServiceProvider(serviceProvider);

    WriteResult wr = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$set:{serviceProvider:#}}", jongoServiceProvider);
    if (wr.getN() != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return jongoServiceProvider;
  }

  @Override
  public ServiceProvider updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider, long[] versions)
      throws InvalidVersionException {

    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("serviceProvider.modified:#");
    updateParameters.add(System.currentTimeMillis());

    if (serviceProvider.getName() != null) {
      updateObject.append(",serviceProvider.name:#");
      updateParameters.add(serviceProvider.getName());
    }

    if (serviceProvider.getScopeCardinalities() != null) {
      updateObject.append(",serviceProvider.scopeCardinalities:#");
      updateParameters.add(serviceProvider.getScopeCardinalities());
    }

    JongoApplication res = getApplicationsCollection()
        .findAndModify("{ serviceProvider.id: #, serviceProvider.modified: { $in: # } }", serviceProviderId, versions)
        .returnNew()
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .projection(SERVICE_PROVIDER_PROJECTION)
        .as(JongoApplication.class);

    if (res == null) {
      if (getApplicationsCollection().count("{ serviceProvider.id : # }", serviceProviderId) != 0) {
        throw new InvalidVersionException("serviceProvider", serviceProviderId);
      }
      logger.warn("More than one service provider with id: {}", serviceProviderId);
      return null;
    }

    return res.getServiceProvider();
  }

  @Override
  public boolean deleteServiceProvider(String serviceProviderId, long[] versions) throws InvalidVersionException {
    WriteResult wr = getApplicationsCollection()
        .update("{ serviceProvider.id: #, serviceProvider.modified: { $in: # } }", serviceProviderId, versions)
        .with("{ $unset: { serviceProvider: '' } }");

    int n = wr.getN();
    if (n == 0) {
      if (getApplicationsCollection().count("{ serviceProvider.id: # }", serviceProviderId) != 0) {
        throw new InvalidVersionException("serviceProvider", serviceProviderId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} serviceProvider with ID {}, that shouldn't have happened", n, serviceProviderId);
    }
    return true;
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection("applications");
  }

  private void populateTenant(JongoApplication app) {
    if (app.getParentApplicationId() == null) {
      logger.error("Parent application not found for tenant : " + app.getId());
      return;
    }

    JongoApplication parent = (JongoApplication) getApplication(app.getParentApplicationId());
    // TODO: "instantiate" scopes
    app.setDataProviders(parent.getDataProviders());
    app.setServiceProvider(parent.getServiceProvider());
  }

  private void decimateTenant(JongoApplication app) {
    app.setDataProviders(null);
    app.setServiceProvider(null);
  }

  private Iterable<JongoApplication> getApplications(String query, int start, int limit) {
    Iterable<JongoApplication> apps = getApplicationsCollection()
        .find(query)
        .skip(start)
        .limit(limit)
        .as(JongoApplication.class);

    return Iterables.transform(apps, new Function<JongoApplication, JongoApplication>() {
      @Nullable
      @Override
      public JongoApplication apply(@Nullable JongoApplication input) {
        if (input == null) {
          return null;
        }

        if (input.isTenant()) {
          populateTenant(input);
        }
        return input;
      }
    });
  }
}
