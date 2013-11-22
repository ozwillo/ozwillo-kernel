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
  public DataProvider getDataProvider(String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Scopes getProvidedScopes(String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createDataProvider(String appId, DataProvider dataProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateDataProvider(String dataProviderId, DataProvider dataProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateDataProviderScopes(String dataProviderId, Scopes scopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteDataProvider(String dataProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<ServiceProvider> getServiceProviders(String appId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServiceProvider getServiceProvider(String serviceProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScopeCardinalities getRequiredScopes(String serviceProviderId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String createServiceProvider(String appId, ServiceProvider serviceProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateServiceProvider(String serviceProviderId, ServiceProvider serviceProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateServiceProviderScopes(String serviceProviderId, ScopeCardinalities scopeCardinalities) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteServiceProvider(String serviceProviderId) {
    throw new UnsupportedOperationException();
  }
}
