package oasis.model.applications.v2;

public interface ApplicationRepository {
  Application getApplication(String applicationId);

  Application createApplication(Application application);
}
