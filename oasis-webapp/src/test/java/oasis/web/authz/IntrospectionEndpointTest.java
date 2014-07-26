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
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.authn.AccessToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.GroupService;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class IntrospectionEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(IntrospectionEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(GroupService.class).in(TestSingleton.class);
    }
  }

  static final ImmutableList<Scope> dp1Scopes = ImmutableList.of(
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s1"); }},
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s2"); }},
      new Scope() {{ setInstance_id("dp1"); setLocal_id("s3"); }}
  );
  static final ImmutableList<Scope> dp2Scopes = ImmutableList.of(
      new Scope() {{ setInstance_id("dp2"); setLocal_id("s1"); }},
      new Scope() {{ setInstance_id("dp2"); setLocal_id("s2"); }}
  );

  static final UserAccount account = new UserAccount() {{
    setId("account");
  }};

  static final AccessToken validToken = new AccessToken() {{
    setId("valid");
    setAccountId(account.getId());
    setScopeIds(ImmutableSet.of("openid", "dp1:s1", "dp1:s3", "dp3:s1"));
    expiresIn(Duration.standardDays(1));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler, AccountRepository accountRepository,
      ScopeRepository scopeRepository, GroupService groupService) {
    when(tokenHandler.getCheckedToken(eq("valid"), any(Class.class))).thenReturn(validToken);
    when(tokenHandler.getCheckedToken(eq("invalid"), any(Class.class))).thenReturn(null);

    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);

    when(scopeRepository.getScopesOfAppInstance(anyString())).thenReturn(Collections.<Scope>emptyList());
    when(scopeRepository.getScopesOfAppInstance("dp1")).thenReturn(dp1Scopes);
    when(scopeRepository.getScopesOfAppInstance("dp2")).thenReturn(dp2Scopes);

    when(groupService.getGroups(account)).thenReturn(Arrays.asList("gp1", "gp2"));
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
    assertThat(response.isActive()).isTrue();
    assertThat(response.getIat()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getCreationTime().getMillis()));
    assertThat(response.getExp()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getExpirationTime().getMillis()));
    assertThat(response.getClient_id()).isEqualTo(validToken.getServiceProviderId());
    assertThat(response.getScope().split(" ")).containsOnly("dp1:s1", "dp1:s3");
    assertThat(response.getSub()).isEqualTo(account.getId());
    assertThat(response.getSub_groups()).containsOnly("gp1", "gp2");
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
