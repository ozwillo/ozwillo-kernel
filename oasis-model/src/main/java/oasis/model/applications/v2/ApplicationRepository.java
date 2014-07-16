package oasis.model.applications.v2;

public interface ApplicationRepository {
  Iterable<Application> getVisibleApplications(); // TODO: remove when we integrate ElasticSearch

  Application getApplication(String applicationId);
}
