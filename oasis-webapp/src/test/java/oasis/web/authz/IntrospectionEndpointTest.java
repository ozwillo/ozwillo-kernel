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
package oasis.web.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.Duration;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.AppAdminHelper;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class IntrospectionEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(IntrospectionEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(AppAdminHelper.class).in(TestSingleton.class);
    }
  }

  static final ImmutableList<Scope> datacoreScopes = ImmutableList.<Scope>of(
      new Scope() {{
        setLocal_id("datacore");
        // Note: computeId must be called BEFORE setInstance_id for the scope ID to be "datacore" (and not "dc:datacore")
        computeId();
        setInstance_id(ClientIds.DATACORE);
      }}
  );
  static final ImmutableList<Scope> dp1Scopes = ImmutableList.of(
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s1"); }},
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s2"); }},
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s3"); }}
  );
  static final ImmutableList<Scope> dp2Scopes = ImmutableList.of(
      new Scope() {{ setInstance_id("dp2"); setLocal_id("s1"); }},
      new Scope() {{ setInstance_id("dp2"); setLocal_id("s2"); }}
  );

  static final AccessToken validToken = new AccessToken() {{
    setId("valid");
    setAccountId("account");
    setServiceProviderId("application");
    setScopeIds(ImmutableSet.of(Scopes.OPENID, "datacore", "dp1:s1", "dp1:s3", "dp3:s1"));
    expiresIn(Duration.standardDays(1));
  }};

  static final AppInstance appInstance = new AppInstance() {{
    setId("application");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @SuppressWarnings("unchecked")
  @Before public void setUpMocks(TokenHandler tokenHandler, ScopeRepository scopeRepository,
      OrganizationMembershipRepository organizationMembershipRepository, AppInstanceRepository appInstanceRepository) {
    when(tokenHandler.getCheckedToken(eq("valid"), any(Class.class))).thenReturn(validToken);
    when(tokenHandler.getCheckedToken(eq("invalid"), any(Class.class))).thenReturn(null);

    when(scopeRepository.getScopesOfAppInstance(anyString())).thenReturn(Collections.<Scope>emptyList());
    when(scopeRepository.getScopesOfAppInstance(ClientIds.DATACORE)).thenReturn(datacoreScopes);
    when(scopeRepository.getScopesOfAppInstance("dp1")).thenReturn(dp1Scopes);
    when(scopeRepository.getScopesOfAppInstance("dp2")).thenReturn(dp2Scopes);

    when(organizationMembershipRepository.getOrganizationIdsForUser("account")).thenReturn(Arrays.asList("org1", "org2"));

    when(appInstanceRepository.getAppInstance("application")).thenReturn(appInstance);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(IntrospectionEndpoint.class);
  }

  @Test public void testEmptyToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp1"));

    // when
    Response resp = introspect("");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  @Test public void testInvalidToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp1"));

    // when
    Response resp = introspect("invalid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  @Test public void testValidToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp1"));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertValidResponse(response, "dp1:s1", "dp1:s3");
    assertThat(response.getSub_groups()).isNullOrEmpty();
  }

  @Test public void testValidTokenAsDataCore() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(ClientIds.DATACORE));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertValidResponse(response, "datacore");
    assertThat(response.getSub_groups()).containsOnly("org1", "org2");
  }

  @Test public void testValidTokenAsDataCoreWithAppAdmin(AppAdminHelper appAdminHelper) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(ClientIds.DATACORE));
    when(appAdminHelper.isAdmin("account", appInstance)).thenReturn(true);

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertValidResponse(response, "datacore");
    assertThat(response.getSub_groups()).containsOnly("org1", "org2", "app_admin_application");
  }

  @Test public void testValidTokenAsDataCoreWithAppUser(AccessControlRepository accessControlRepository) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(ClientIds.DATACORE));
    when(accessControlRepository.getAccessControlEntry("application", "account")).thenReturn(new AccessControlEntry());

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertValidResponse(response, "datacore");
    assertThat(response.getSub_groups()).containsOnly("org1", "org2", "app_user_application");
  }

  @Test public void testValidTokenAsDataCoreWithAppAdminAndAppUser(AppAdminHelper appAdminHelper, AccessControlRepository accessControlRepository) {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(ClientIds.DATACORE));
    when(appAdminHelper.isAdmin("account", appInstance)).thenReturn(true);
    when(accessControlRepository.getAccessControlEntry("application", "account")).thenReturn(new AccessControlEntry());

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertValidResponse(response, "datacore");
    assertThat(response.getSub_groups()).containsOnly("org1", "org2", "app_admin_application", "app_user_application");
  }

  private void assertValidResponse(IntrospectionResponse response, String... expectedScopes) {
    assertThat(response.isActive()).isTrue();
    assertThat(response.getIat()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getCreationTime().getMillis()));
    assertThat(response.getExp()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getExpirationTime().getMillis()));
    assertThat(response.getClient_id()).isEqualTo(validToken.getServiceProviderId());
    assertThat(response.getScope().split(" ")).containsOnly(expectedScopes);
    assertThat(response.getSub()).isEqualTo("account");
  }

  @Test public void testStolenToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("dp2"));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  private Response introspect(String token) {
    return resteasy.getClient().target(UriBuilder.fromResource(IntrospectionEndpoint.class)).request()
        .post(Entity.form(new Form("token", token)));
  }
}
