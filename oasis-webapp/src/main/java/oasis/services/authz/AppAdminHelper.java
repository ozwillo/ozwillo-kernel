/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
