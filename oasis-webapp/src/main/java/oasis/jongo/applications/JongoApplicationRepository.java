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

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ServiceProvider;

public class JongoApplicationRepository implements ApplicationRepository {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

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
        .projection("{id:1, name:1, iconUri:1, modified:1}")
        .as(JongoApplication.class);

    if (app.isTenant()) {
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
  public void updateApplication(String appId, Application app) {

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

    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray());

    if (wr.getN() != 1) {
      logger.warn("More than one application with id: {}", appId);
    }
  }

  @Override
  public boolean deleteApplication(String appId) {
    // TODO : check modified
    WriteResult wr = getApplicationsCollection().remove("{id: #}", appId);

    return wr.getN() == 1;
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
        .projection("{ id:1, dataProviders.$:1 }")
        .as(JongoApplication.class);
    if (app == null || app.getDataProviders().isEmpty()) {
      return null;
    }

    return app.getDataProviders().get(0);
  }

  @Override
  public DataProvider createDataProvider(String appId, DataProvider dataProvider) {

    JongoDataProvider jongoDataProvider = new JongoDataProvider(dataProvider);

    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$push:{dataProviders:#}}", jongoDataProvider);
    if (wr.getN() != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return jongoDataProvider;
  }

  @Override
  public void updateDataProvider(String dataProviderId, DataProvider dataProvider) {
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

    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{dataProviders.id:#}", dataProviderId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray());

    if (wr.getN() != 1) {
      logger.warn("More than one data provider with id: {}", dataProviderId);
    }
  }

  @Override
  public boolean deleteDataProvider(String dataProviderId) {
    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{dataProviders.id:#}", dataProviderId)
        .with("{$pull:{dataProviders: {id:#}}}", dataProviderId);

    if (wr.getN() != 1) {
      logger.warn("More than one data provider with id: {}", dataProviderId);
    }
    return wr.getN() == 1;
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
        .projection("{id:1, serviceProvider:1 }")
        .as(JongoApplication.class);
    if (app == null) {
      return null;
    }
    return app.getServiceProvider();
  }

  @Override
  public ServiceProvider createServiceProvider(String appId, ServiceProvider serviceProvider) {
    JongoServiceProvider jongoServiceProvider = new JongoServiceProvider(serviceProvider);

    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$set:{serviceProvider:#}}", jongoServiceProvider);
    if (wr.getN() != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return jongoServiceProvider;
  }

  @Override
  public void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider) {
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

    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{serviceProvider.id:#}", serviceProviderId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray());

    if (wr.getN() != 1) {
      logger.warn("More than one service provider with id: {}", serviceProviderId);
    }
  }

  @Override
  public boolean deleteServiceProvider(String serviceProviderId) {
    // TODO : check modified
    WriteResult wr = getApplicationsCollection()
        .update("{serviceProvider.id:#}", serviceProviderId)
        .with("{$unset:{serviceProvider:''}}");

    if (wr.getN() != 1) {
      logger.warn("More than one service provider with id: {}", serviceProviderId);
    }
    return wr.getN() == 1;
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
