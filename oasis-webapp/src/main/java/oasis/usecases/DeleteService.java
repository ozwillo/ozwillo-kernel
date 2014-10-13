package oasis.usecases;

import javax.inject.Inject;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;

public class DeleteService {

  private final ServiceRepository serviceRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;

  @Inject DeleteService(ServiceRepository serviceRepository, UserSubscriptionRepository userSubscriptionRepository) {
    this.serviceRepository = serviceRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
  }

  public Status deleteService(String service_id, long[] versions) {
    boolean deleted = false;
    try {
      deleted = serviceRepository.deleteService(service_id, versions);
    } catch (InvalidVersionException e) {
      return Status.BAD_SERVICE_VERSION;
    }

    int deletedSubscriptions = userSubscriptionRepository.deleteSubscriptionsForService(service_id);

    if (deleted) {
      return Status.DELETED_SERVICE;
    } else if (deletedSubscriptions == 0) {
      return Status.NOTHING_TO_DELETE;
    }
    return Status.DELETED_LEFTOVERS;
  }

  public enum Status {
    BAD_SERVICE_VERSION,
    DELETED_SERVICE,
    DELETED_LEFTOVERS,
    NOTHING_TO_DELETE
  }
}
