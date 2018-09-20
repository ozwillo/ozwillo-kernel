/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.web.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Address;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccessToken;
import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.web.authn.testing.TestOAuthFilter;

@RunWith(JukitoRunner.class)
public class UserEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserEndpoint.class);
    }
  }

  private static final UserAccount userAccount = new UserAccount() {{
    setId("account id");
    setEmail_address("foo@example.net");
    setEmail_verified(true);
    setLocale(ULocale.FRANCE);
    setGiven_name("account given name");
    setFamily_name("account family name");
    setMiddle_name("account middle name");
    setNickname("account nickname");
    setGender("account gender");
    setBirthdate(LocalDate.of(2000, Month.MARCH, 6));
    setPhone_number("+33380688168");
    setPhone_number_verified(true);
    Address address = new Address();
    address.setStreet_address("ZAE Les Terres d'Or\nRoute de Saint-Philibert");
    address.setPostal_code("21220");
    address.setLocality("GEVREY-CHAMBERTIN");
    address.setCountry("FRANCE");
    setCreated_at(952297200000L);
    setUpdated_at(1312322400000L);
  }};

  private static final UserAccount userAccountWithFC = new UserAccount(userAccount) {{
    setId("account with FC id");
    setFranceconnect_sub("franceconnect sub");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Inject public CredentialsRepository credentialsRepository;

  @Before
  public void setUpMocks(AccountRepository accountRepository) {
    when(accountRepository.getUserAccountById(userAccount.getId())).thenReturn(userAccount);
    when(accountRepository.getUserAccountById(userAccountWithFC.getId())).thenReturn(userAccountWithFC);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(UserEndpoint.class);
  }

  @Test public void testGet_fromApplication() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccount.getId());
      setServiceProviderId("app_instance");
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccount.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    UserAccount user = response.readEntity(UserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new AppUserAccount(userAccount));

    verifyZeroInteractions(credentialsRepository);
  }

  @Test public void testGet_fromApplication_forOtherAccount() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccount.getId());
      setServiceProviderId("app_instance");
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccountWithFC.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    AppUserAccount user = response.readEntity(AppUserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new AppUserAccount(userAccountWithFC));

    verifyZeroInteractions(credentialsRepository);
  }

  @Test public void testGet_fromPortal() {
    when(credentialsRepository.getCredentials(ClientType.USER, userAccount.getId())).thenReturn(new Credentials() {{
      setClientType(ClientType.USER);
      setId(userAccount.getId());
    }});

    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccount.getId());
      setServiceProviderId(ClientIds.PORTAL);
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccount.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    PortalUserAccount user = response.readEntity(PortalUserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new PortalUserAccount(userAccount) {{
      authentication_methods = ImmutableList.of("pwd");
    }});
  }

  @Test public void testGet_fromPortal_forOtherAccount() {
    when(credentialsRepository.getCredentials(ClientType.USER, userAccountWithFC.getId())).thenReturn(new Credentials() {{
      setClientType(ClientType.USER);
      setId(userAccountWithFC.getId());
    }});

    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccount.getId());
      setServiceProviderId(ClientIds.PORTAL);
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccountWithFC.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    PortalUserAccount user = response.readEntity(PortalUserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new PortalUserAccount(userAccountWithFC) {{
      authentication_methods = ImmutableList.of("pwd", "franceconnect");
    }});
  }

  @Test public void testGet_fromPortal_withoutPassword() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccountWithFC.getId());
      setServiceProviderId(ClientIds.PORTAL);
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccountWithFC.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    PortalUserAccount user = response.readEntity(PortalUserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new PortalUserAccount(userAccountWithFC) {{
      authentication_methods = ImmutableList.of("franceconnect");
    }});

    verify(credentialsRepository).getCredentials(ClientType.USER, userAccountWithFC.getId());
  }

  @Test public void testGet_fromPortal_forOtherAccount_withoutPassword() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(userAccount.getId());
      setServiceProviderId(ClientIds.PORTAL);
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserEndpoint.class).build(userAccountWithFC.getId()))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    PortalUserAccount user = response.readEntity(PortalUserAccount.class);
    assertThat(user).isEqualToComparingFieldByFieldRecursively(new PortalUserAccount(userAccountWithFC) {{
      authentication_methods = ImmutableList.of("franceconnect");
    }});

    verify(credentialsRepository).getCredentials(ClientType.USER, userAccountWithFC.getId());
  }

  // TODO: test PUT

  static class PortalUserAccount extends UserAccount {
    @JsonProperty List<String> authentication_methods;

    PortalUserAccount() {}
    PortalUserAccount(UserAccount userAccount) {
      super(userAccount);
      setId(userAccount.getId());
    }
  }

  static class AppUserAccount extends UserAccount {
    @JsonProperty String name;

    AppUserAccount() {}
    AppUserAccount(UserAccount userAccount) {
      super();
      setId(userAccount.getId());
      setNickname(userAccount.getNickname());
      name = userAccount.getName();
    }
  }
}
