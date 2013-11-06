package oasis.model.applications;

import java.util.Collection;

public interface ApplicationRepository {
  public Collection<Application> getApplications(int start, int limit);

  public Application getApplication(String appId);

  public String createApplication(Application app);

  public void updateApplication(String appId, Application app);

  public void deleteApplication(String appId);

  public Collection<DataProvider> getDataProviders(String appId);

  public DataProvider getDataProvider(String applicationId, String dataProviderId);

  public Scopes getProvidedScopes(String applicationId, String dataProviderId);

  public String createDataProvider(String applicationId, DataProvider dataProvider);

  public void updateDataProvider(String applicationId, String dataProviderId, DataProvider dataProvider);

  public void updateDataProviderScopes(String applicationId, String dataProviderId, Scopes scopes);

  public void deleteDataProvider(String applicationId, String dataProviderId);

  public Collection<ServiceProvider> getServiceProviders(String appId);

  public ServiceProvider getServiceProvider(String applicationId, String serviceProviderId);

  public ScopeCardinalities getRequiredScopes(String applicationId, String serviceProviderId);

  public String createServiceProvider(String applicationId, ServiceProvider serviceProvider);

  public void updateServiceProvider(String applicationId, String serviceProviderId, ServiceProvider serviceProvider);

  public void updateServiceProviderScopes(String applicationId, String serviceProviderId, ScopeCardinalities scopeCardinalities);

  public void deleteServiceProvider(String applicationId, String serviceProviderId);
}
