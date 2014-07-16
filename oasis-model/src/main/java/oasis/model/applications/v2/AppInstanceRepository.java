package oasis.model.applications.v2;

import java.util.List;

import oasis.model.InvalidVersionException;

public interface AppInstanceRepository {
  AppInstance createAppInstance(AppInstance appInstance);

  AppInstance getAppInstance(String instanceId);

  boolean instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes);

  boolean deletePendingInstance(String instanceId);
}
