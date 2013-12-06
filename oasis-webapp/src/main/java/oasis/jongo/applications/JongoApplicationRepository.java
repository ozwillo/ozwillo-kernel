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

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ScopeCardinalities;
import oasis.model.applications.Scopes;
import oasis.model.applications.ServiceProvider;

public class JongoApplicationRepository implements ApplicationRepository {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

  @Inject
  private Jongo jongo;

  @Override
  public Iterable<Application> getApplicationInstances(int start, int limit) {
    return getApplications("{applicationType:'INSTANCE'}", start, limit);
  }

  @Override
  public Iterable<Application> getCatalogApplications(int start, int limit) {
    return getApplications("{exposed:true}", start, limit);
  }

  @Override
  public Application getApplication(String appId) {
    Application app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection("{id:1, name:1, iconUri:1, modified:1}")
        .as(Application.class);

    if (app.isTenant()) {
      populateTenant(app);
    }
    return app;
  }

  @Override
  public String createApplication(Application app) {
    app.setModified(System.nanoTime());
    getApplicationsCollection()
        .insert(app);
    return app.getId();
  }

  @Override
  public void updateApplication(String appId, Application app) {
    if (app.isTenant()
        && (app.getDataProviders() != null || app.getServiceProvider() != null)) {
      logger.warn("Tenant applications cannot specify providers");
      decimateTenant(app);
    }

    long modified = System.nanoTime();
    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("modified:#");
    updateParameters.add(modified);

    if (app.getName() != null) {
      updateObject.append(",name:#");
      updateParameters.add(app.getName());
    }

    if (app.getIconUri() != null) {
      updateObject.append(",iconUri:#");
      updateParameters.add(app.getIconUri());
    }

    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one application with id: {}", appId);
    }
    app.setModified(modified);
  }

  @Override
  public void deleteApplication(String appId) {
    // TODO : check modified
    getApplicationsCollection().remove("{id: #}", appId);
  }

  @Override
  public Iterable<DataProvider> getDataProviders(String appId) {
    Application app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection("{id:1, dataProviders:1, applicationType: 1, instanciationType: 1, parentApplicationId: 1 }")
        .as(Application.class);
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

    return app.getDataProviders();
  }

  @Override
  public DataProvider getDataProvider(String dataProviderId) {
    Application app = getApplicationsCollection()
        .findOne("{dataProviders.id: # }", dataProviderId)
        .projection("{ id:1, dataProviders.$:1 }")
        .as(Application.class);
    if (app == null || app.getDataProviders().isEmpty()) {
      return null;
    }

    return app.getDataProviders().get(0);
  }

  @Override
  public Scopes getProvidedScopes(String dataProviderId) {
    DataProvider dp = getDataProvider(dataProviderId);
    Scopes res = new Scopes();
    res.setDataProviderId(dataProviderId);
    res.setValues(dp.getScopes());
    res.setModified(dp.getModified());
    return res;
  }

  @Override
  public String createDataProvider(String appId, DataProvider dataProvider) {
    long modified = System.nanoTime();
    dataProvider.setModified(modified);

    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$push:{dataProviders:#}}", dataProvider)
        .getN();
    if (nbResults != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return dataProvider.getId();
  }

  @Override
  public void updateDataProvider(String dataProviderId, DataProvider dataProvider) {
    long modified = System.nanoTime();
    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("dataProviders.$.modified:#");
    updateParameters.add(modified);

    if (dataProvider.getName() != null) {
      updateObject.append(",dataProviders.$.name:#");
      updateParameters.add(dataProvider.getName());
    }

    if (dataProvider.getScopes() != null) {
      updateObject.append(",dataProviders.$.scopes:#");
      updateParameters.add(dataProvider.getScopes());
    }

    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{dataProviders.id:#}", dataProviderId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one data provider with id: {}", dataProviderId);
    }
    dataProvider.setModified(modified);
  }

  @Override
  public void updateDataProviderScopes(String dataProviderId, Scopes scopes) {
    long modified = System.nanoTime();
    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{dataProviders.id:#}", dataProviderId)
        .with("{$set: {dataProviders.$.scopes:#, dataProviders.$.modified:#}}", scopes.getValues(), modified)
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one data provider with id: {}", dataProviderId);
    }
    scopes.setModified(modified);
  }

  @Override
  public void deleteDataProvider(String dataProviderId) {
    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{dataProviders.id:#}", dataProviderId)
        .with("{$pull:{dataProviders: {id:#}}}", dataProviderId)
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one data provider with id: {}", dataProviderId);
    }
  }

  @Override
  public ServiceProvider getServiceProviderFromApplication(String appId) {
    Application app = getApplicationsCollection()
        .findOne("{id: #}", appId)
        .projection("{id:1, serviceProvider:1, instanciationType: 1, applicationType: 1, parentApplicationId: 1 }")
        .as(Application.class);
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
    Application app = getApplicationsCollection()
        .findOne("{serviceProvider.id: # }", serviceProviderId)
        .projection("{id:1, serviceProvider:1 }")
        .as(Application.class);
    if (app == null) {
      return null;
    }
    return app.getServiceProvider();
  }

  @Override
  public ScopeCardinalities getRequiredScopes(String serviceProviderId) {
    ServiceProvider sp = getServiceProvider(serviceProviderId);
    if (sp == null) {
      logger.warn("The service provider {} does not exist.");
      return null;
    }
    ScopeCardinalities res = new ScopeCardinalities();
    res.setServiceProviderId(serviceProviderId);
    res.setValues(sp.getScopeCardinalities());
    res.setModified(sp.getModified());
    return res;
  }

  @Override
  public String createServiceProvider(String appId, ServiceProvider serviceProvider) {
    long modified = System.nanoTime();
    serviceProvider.setModified(modified);

    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{id: #}", appId)
        .with("{$set:{serviceProvider:#}}", serviceProvider)
        .getN();
    if (nbResults != 1) {
      logger.warn("More than one application with id: {}", appId);
    }

    return serviceProvider.getId();
  }

  @Override
  public void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider) {
    long modified = System.nanoTime();
    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("serviceProvider.modified:#");
    updateParameters.add(modified);

    if (serviceProvider.getName() != null) {
      updateObject.append(",serviceProvider.name:#");
      updateParameters.add(serviceProvider.getName());
    }

    if (serviceProvider.getScopeCardinalities() != null) {
      updateObject.append(",serviceProvider.scopeCardinalities:#");
      updateParameters.add(serviceProvider.getScopeCardinalities());
    }

    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{serviceProvider.id:#}", serviceProviderId)
        .with("{$set: {" + updateObject.toString() + "}}", updateParameters.toArray())
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one service provider with id: {}", serviceProviderId);
    }
    serviceProvider.setModified(modified);
  }

  @Override
  public void updateServiceProviderScopes(String serviceProviderId, ScopeCardinalities scopeCardinalities) {
    long modified = System.nanoTime();
    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{serviceProvider.id:#}", serviceProviderId)
        .with("{$set: {serviceProvider.scopeCardinalities:#, serviceProvider.modified:#}}", scopeCardinalities.getValues(), modified)
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one service provider with id: {}", serviceProviderId);
    }
    scopeCardinalities.setModified(modified);
  }

  @Override
  public void deleteServiceProvider(String serviceProviderId) {
    // TODO : check modified
    int nbResults = getApplicationsCollection()
        .update("{serviceProvider.id:#}", serviceProviderId)
        .with("{$unset:{serviceProvider:''}}")
        .getN();

    if (nbResults != 1) {
      logger.warn("More than one service provider with id: {}", serviceProviderId);
    }
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection("applications");
  }

  private void populateTenant(Application app) {
    if (app.getParentApplicationId() != null) {
      Application parent = getApplication(app.getParentApplicationId());
      // TODO: "instantiate" scopes
      app.setDataProviders(parent.getDataProviders());
      app.setServiceProvider(parent.getServiceProvider());
    } else {
      logger.error("Parent application not found for tenant : " + app.getId());
    }
  }

  private void decimateTenant(Application app) {
    app.setDataProviders(null);
    app.setServiceProvider(null);
  }

  private Iterable<Application> getApplications(String query, int start, int limit) {
    Iterable<Application> apps = getApplicationsCollection()
        .find(query)
        .skip(start)
        .limit(limit)
        .as(Application.class);

    return Iterables.transform(apps, new Function<Application, Application>() {
      @Nullable
      @Override
      public Application apply(@Nullable Application input) {
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
