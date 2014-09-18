package oasis.model.applications.v2;

import java.util.List;

public interface AppInstanceRepository {
  AppInstance createAppInstance(AppInstance appInstance);

  AppInstance getAppInstance(String instanceId);

  Iterable<AppInstance> findByOrganizationId(String organizationId);

  Iterable<AppInstance> findPersonalInstancesByUserId(String userId);

  AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes);

  boolean deletePendingInstance(String instanceId);

  boolean deleteInstance(String instanceId);

  Iterable<AppInstance> getInstancesForApplication(String applicationId);
}
