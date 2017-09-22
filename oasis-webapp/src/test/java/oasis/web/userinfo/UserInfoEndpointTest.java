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

import com.google.common.collect.ImmutableSet;
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
import oasis.urls.UrlsModule;
import oasis.web.authn.testing.TestOAuthFilter;

@RunWith(JukitoRunner.class)
public class UserInfoEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserInfoEndpoint.class);

      install(new UrlsModule(ImmutableUrls.builder().build()));

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
      setScopeIds(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE));
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserInfoEndpoint.class))
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    // TODO: check content
    assertThat(response.readEntity(String.class)).contains(citizenAccount.getLocale().toLanguageTag());
  }

  @Test public void testJwtIfAskedFor() {
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId(citizenAccount.getId());
    }}));

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserInfoEndpoint.class))
        .request()
        .accept("application/jwt", "application/json; q=0.9")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getMediaType()).isEqualTo(new MediaType("application", "jwt"));
    // TODO: check content
  }
}
