package oasis.model.applications.v2;

import java.util.Collection;
import java.util.List;

public interface AppInstanceRepository {
  AppInstance createAppInstance(AppInstance appInstance);

  AppInstance getAppInstance(String instanceId);

  Iterable<AppInstance> getAppInstances(Collection<String> instanceIds);

  Iterable<AppInstance> findByOrganizationId(String organizationId);

  Iterable<AppInstance> findPersonalInstancesByUserId(String userId);

  AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes, String destruction_uri, String destruction_secret);

  boolean deleteInstance(String instanceId);

  Iterable<AppInstance> getInstancesForApplication(String applicationId);
}
