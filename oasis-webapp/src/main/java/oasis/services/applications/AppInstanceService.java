package oasis.services.applications;

import java.util.List;

import javax.inject.Inject;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.ScopeRepository;

public class AppInstanceService {
  private final AppInstanceRepository appInstanceRepository;

  @Inject AppInstanceService(AppInstanceRepository appInstanceRepository) {
    this.appInstanceRepository = appInstanceRepository;
  }

  public AppInstance getAppInstance(String instanceId) {
    return appInstanceRepository.getAppInstance(instanceId);
  }

  public AppInstance createAppInstance(AppInstance instance) {
    return appInstanceRepository.createAppInstance(instance);
  }

  public boolean instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes) {
    return appInstanceRepository.instantiated(instanceId, neededScopes);
  }

  public boolean deletePendingInstance(String instanceId) {
    return appInstanceRepository.deletePendingInstance(instanceId);
  }
}
