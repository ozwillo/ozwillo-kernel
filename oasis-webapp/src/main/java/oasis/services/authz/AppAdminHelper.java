package oasis.services.authz;

import java.util.Collections;

import javax.inject.Inject;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;

import oasis.model.applications.v2.AppInstance;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;

public class AppAdminHelper {
  private final OrganizationMembershipRepository organizationMembershipRepository;

  @Inject AppAdminHelper(OrganizationMembershipRepository organizationMembershipRepository) {
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

  public Iterable<String> getAdmins(AppInstance appInstance) {
    if (Strings.isNullOrEmpty(appInstance.getProvider_id())) {
      // Application bought by an individual
      return Collections.singletonList(appInstance.getInstantiator_id());
    }
    // Application bought by/for an organization
    return FluentIterable.from(organizationMembershipRepository.getAdminsOfOrganization(appInstance.getProvider_id()))
        .transform(new Function<OrganizationMembership, String>() {
          @Override
          public String apply(OrganizationMembership organizationMembership) {
            return organizationMembership.getAccountId();
          }
        });
  }
}
