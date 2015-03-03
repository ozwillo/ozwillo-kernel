package oasis.model.applications.v2;

import java.util.Collection;
import java.util.List;

import oasis.model.InvalidVersionException;

public interface AppInstanceRepository {
  AppInstance createAppInstance(AppInstance appInstance);

  AppInstance getAppInstance(String instanceId);

  Iterable<AppInstance> getAppInstances(Collection<String> instanceIds);

  Iterable<AppInstance> findByOrganizationId(String organizationId);

  Iterable<AppInstance> findByOrganizationIdAndStatus(String organizationId, AppInstance.InstantiationStatus instantiationStatus);

  Iterable<AppInstance> findPersonalInstancesByUserId(String userId);

  Iterable<AppInstance> findPersonalInstancesByUserIdAndStatus(String userId, AppInstance.InstantiationStatus instantiationStatus);

  AppInstance updateStatus(String instanceId, AppInstance.InstantiationStatus newStatus, String statusChangeRequesterId, long[] versions)
      throws InvalidVersionException;

  AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes, String destruction_uri, String destruction_secret);

  AppInstance backToPending(String instanceId);

  boolean deleteInstance(String instanceId, long[] versions) throws InvalidVersionException;

  boolean deleteInstance(String instanceId);

  Iterable<AppInstance> getInstancesForApplication(String applicationId);
}
