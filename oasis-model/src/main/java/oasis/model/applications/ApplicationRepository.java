package oasis.model.applications;

public interface ApplicationRepository {
  Iterable<Application> getCatalogApplications(int start, int limit);

  Iterable<Application> getApplicationInstances(int start, int limit);

  Application getApplication(String appId);

  Application createApplication(Application app);

  Application instanciateApplication(String appId, String organizationId);

  Application updateApplication(String appId, Application app);

  boolean deleteApplication(String appId);

  Iterable<DataProvider> getDataProviders(String appId);

  DataProvider getDataProvider(String dataProviderId);

  DataProvider createDataProvider(String appId, DataProvider dataProvider);

  DataProvider updateDataProvider(String dataProviderId, DataProvider dataProvider);

  boolean deleteDataProvider(String dataProviderId);

  ServiceProvider getServiceProviderFromApplication(String appId);

  ServiceProvider getServiceProvider(String serviceProviderId);

  ServiceProvider createServiceProvider(String appId, ServiceProvider serviceProvider);

  ServiceProvider updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider);

  boolean deleteServiceProvider(String serviceProviderId);
}
