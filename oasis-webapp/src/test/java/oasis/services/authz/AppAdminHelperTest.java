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
