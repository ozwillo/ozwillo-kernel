package oasis.web.userinfo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccessToken;
import oasis.model.authz.Scopes;
import oasis.security.KeyPairLoader;
import oasis.urls.ImmutableUrls;
import oasis.urls.Urls;
import oasis.urls.UrlsModule;
import oasis.web.authn.testing.TestOAuthFilter;

@RunWith(JukitoRunner.class)
public class UserInfoEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserInfoEndpoint.class);

      install(new UrlsModule(ImmutableUrls.builder().build()));

      bind(JsonFactory.class).to(JacksonFactory.class);

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());
    }
  }

  private static final UserAccount citizenAccount = new UserAccount() {{
    setId("citizen");
    setLocale(ULocale.ITALY);
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AccountRepository accountRepository) {
    when(accountRepository.getUserAccountById(citizenAccount.getId())).thenReturn(citizenAccount);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(UserInfoEndpoint.class);
  }

  /**
   * Per <a href="http://openid.net/specs/openid-connect-core-1_0.html#UserInfoResponse">spec</a>,
   * the response should be JSON by default.
   */
  @Test public void testJsonByDefault() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(citizenAccount.getId());
      setScopeIds(Sets.newHashSet(Scopes.OPENID, Scopes.PROFILE));
    }}));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(UserInfoEndpoint.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    // TODO: check content
    assertThat(response.readEntity(String.class)).contains(citizenAccount.getLocale().toLanguageTag());
  }

  @Test public void testJwtIfAskedFor() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(citizenAccount.getId());
    }}));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(UserInfoEndpoint.class)).request()
        .accept("application/jwt", "application/json; q=0.9")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(new MediaType("application", "jwt"));
    // TODO: check content
  }
}
