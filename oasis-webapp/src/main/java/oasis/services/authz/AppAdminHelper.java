package oasis.services.authz;

import javax.inject.Inject;

import com.google.common.base.Strings;

import oasis.model.applications.v2.AppInstance;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;

public class AppAdminHelper {
  private final OrganizationMembershipRepository organizationMembershipRepository;

  @Inject
  AppAdminHelper(OrganizationMembershipRepository organizationMembershipRepository) {
    this.organizationMembershipRepository = organizationMembershipRepository;
  }

  public boolean isAdmin(String userId, AppInstance instance) {
    // TODO: support applications bought by individuals
    if (Strings.isNullOrEmpty(instance.getProvider_id())) {
      // Application bought by an individual
      return userId.equals(instance.getInstantiator_id());
    } else {
      // Application bought by/for an organization
      OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(userId, instance.getProvider_id());
      return membership != null && membership.isAdmin();
    }
  }
}
