package oasis.services.applications;

import java.util.Collection;

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ScopeCardinalities;
import oasis.model.applications.Scopes;
import oasis.model.applications.ServiceProvider;

public class DummyApplicationRepository implements ApplicationRepository {
  @Override
  public Collection<Application> getApplications(int start, int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Application getApplication(String appId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createApplication(Application app) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateApplication(String appId, Application app) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteApplication(String appId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<DataProvider> getDataProviders(String appId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DataProvider getDataProvider(String applicationId, String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Scopes getProvidedScopes(String applicationId, String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createDataProvider(String applicationId, DataProvider dataProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateDataProvider(String applicationId, String dataProviderId, DataProvider dataProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateDataProviderScopes(String applicationId, String dataProviderId, Scopes scopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteDataProvider(String applicationId, String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<ServiceProvider> getServiceProviders(String appId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServiceProvider getServiceProvider(String applicationId, String serviceProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScopeCardinalities getRequiredScopes(String applicationId, String serviceProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createServiceProvider(String applicationId, ServiceProvider serviceProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateServiceProvider(String applicationId, String serviceProviderId, ServiceProvider serviceProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateServiceProviderScopes(String applicationId, String serviceProviderId, ScopeCardinalities scopeCardinalities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteServiceProvider(String applicationId, String serviceProviderId) {
    throw new UnsupportedOperationException();
  }
}
