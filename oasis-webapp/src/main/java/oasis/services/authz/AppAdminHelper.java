package oasis.services.authz;

import javax.inject.Inject;

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
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(userId, instance.getProvider_id());
    return membership != null && membership.isAdmin();
  }
}
