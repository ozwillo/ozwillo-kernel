/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import javax.inject.Inject;

import org.jukito.JukitoRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import oasis.model.applications.v2.AppInstance;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;

@RunWith(JukitoRunner.class)
public class AppAdminHelperTest {

  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @Test public void admin_of_provider_org_is_app_admin() {
    AppInstance instance = new AppInstance() {{
      setId("instance");
      setProvider_id("org");
      setInstantiator_id("instantiator");
    }};
    when(organizationMembershipRepository.getOrganizationMembership("user", "org"))
        .thenReturn(new OrganizationMembership() {{
          setId("membership");
          setAccountId("user");
          setOrganizationId("org");
          setAdmin(true);
        }});

    boolean isAppAdmin = new AppAdminHelper(organizationMembershipRepository).isAdmin("user", instance);

    assertThat(isAppAdmin).isTrue();
  }

  @Test public void non_admin_of_provider_org_is_not_app_admin() { // even if instantiator!
    AppInstance instance = new AppInstance() {{
      setId("instance");
      setProvider_id("org");
      setInstantiator_id("instantiator");
    }};
    when(organizationMembershipRepository.getOrganizationMembership("instantiator", "org"))
        .thenReturn(new OrganizationMembership() {{
          setId("membership");
          setAccountId("instantiator");
          setOrganizationId("org");
          setAdmin(false);
        }});

    boolean isAppAdmin = new AppAdminHelper(organizationMembershipRepository).isAdmin("instantiator", instance);

    assertThat(isAppAdmin).isFalse();
  }

  @Test public void non_member_of_provider_org_is_not_app_admin() { // even if instantiator
    AppInstance instance = new AppInstance() {{
      setId("instance");
      setProvider_id("org");
      setInstantiator_id("instantiator");
    }};

    boolean isAppAdmin = new AppAdminHelper(organizationMembershipRepository).isAdmin("instantiator", instance);

    assertThat(isAppAdmin).isFalse();
  }
}
