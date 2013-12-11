package oasis.model.applications;

import oasis.model.InvalidVersionException;

public interface ApplicationRepository {
  Iterable<Application> getCatalogApplications(int start, int limit);

  Iterable<Application> getApplicationInstances(int start, int limit);

  Application getApplication(String appId);

  Application createApplication(Application app);

  Application instanciateApplication(String appId, String organizationId);

  Application updateApplication(String appId, Application app, long[] versions) throws InvalidVersionException;

  boolean deleteApplication(String appId, long[] versions) throws InvalidVersionException;

  Iterable<DataProvider> getDataProviders(String appId);

  DataProvider getDataProvider(String dataProviderId);

  DataProvider createDataProvider(String appId, DataProvider dataProvider);

  DataProvider updateDataProvider(String dataProviderId, DataProvider dataProvider, long[] versions) throws InvalidVersionException;

  boolean deleteDataProvider(String dataProviderId, long[] versions) throws InvalidVersionException;

  ServiceProvider getServiceProviderFromApplication(String appId);

  ServiceProvider getServiceProvider(String serviceProviderId);

  ServiceProvider createServiceProvider(String appId, ServiceProvider serviceProvider);

  ServiceProvider updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider, long[] versions) throws InvalidVersionException;

  boolean deleteServiceProvider(String serviceProviderId, long[] versions) throws InvalidVersionException;
}
