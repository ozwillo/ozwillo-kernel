package oasis.services.applications;

import javax.inject.Inject;

import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;

public class ApplicationService {

  private final ApplicationRepository applicationRepository;

  @Inject
  ApplicationService(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  public Iterable<Application> getVisibleApplications() {
    return applicationRepository.getVisibleApplications();
  }

  public Application getApplication(String applicationId) {
    return applicationRepository.getApplication(applicationId);
  }
}
