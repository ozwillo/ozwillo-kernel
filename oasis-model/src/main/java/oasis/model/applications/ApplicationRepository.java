package oasis.model.applications;

public interface ApplicationRepository {
  Iterable<Application> getCatalogApplications(int start, int limit);

  Iterable<Application> getApplicationInstances(int start, int limit);

  Application getApplication(String appId);

  String createApplication(Application app);

  void updateApplication(String appId, Application app);

  void deleteApplication(String appId);

  Iterable<DataProvider> getDataProviders(String appId);

  DataProvider getDataProvider(String dataProviderId);

  Scopes getProvidedScopes(String dataProviderId);

  String createDataProvider(String appId, DataProvider dataProvider);

  void updateDataProvider(String dataProviderId, DataProvider dataProvider);

  void updateDataProviderScopes(String dataProviderId, Scopes scopes);

  void deleteDataProvider(String dataProviderId);

  ServiceProvider getServiceProviderFromApplication(String appId);

  ServiceProvider getServiceProvider(String serviceProviderId);

  ScopeCardinalities getRequiredScopes(String serviceProviderId);

  String createServiceProvider(String appId, ServiceProvider serviceProvider);

  void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider);

  void updateServiceProviderScopes(String serviceProviderId, ScopeCardinalities scopeCardinalities);

  void deleteServiceProvider(String serviceProviderId);
}
