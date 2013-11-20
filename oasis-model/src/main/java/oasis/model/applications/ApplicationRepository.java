package oasis.model.applications;

import java.util.Collection;

public interface ApplicationRepository {
  public Collection<Application> getApplications(int start, int limit);

  public Application getApplication(String appId);

  public String createApplication(Application app);

  public void updateApplication(String appId, Application app);

  public void deleteApplication(String appId);

  public Collection<DataProvider> getDataProviders(String appId);

  public DataProvider getDataProvider(String dataProviderId);

  public Scopes getProvidedScopes(String dataProviderId);

  public String createDataProvider(DataProvider dataProvider);

  public void updateDataProvider(String dataProviderId, DataProvider dataProvider);

  public void updateDataProviderScopes(String dataProviderId, Scopes scopes);

  public void deleteDataProvider(String dataProviderId);

  public Collection<ServiceProvider> getServiceProviders(String appId);

  public ServiceProvider getServiceProvider(String serviceProviderId);

  public ScopeCardinalities getRequiredScopes(String serviceProviderId);

  public String createServiceProvider(ServiceProvider serviceProvider);

  public void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider);

  public void updateServiceProviderScopes(String serviceProviderId, ScopeCardinalities scopeCardinalities);

  public void deleteServiceProvider(String serviceProviderId);
}
