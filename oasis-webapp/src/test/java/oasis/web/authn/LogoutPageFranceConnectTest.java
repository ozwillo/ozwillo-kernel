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
package oasis.web.authn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.FranceConnectModule;
import oasis.auth.ImmutableFranceConnectModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.model.i18n.LocalizableString;
import oasis.security.KeyPairLoader;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.UrlsModule;
import oasis.web.authn.franceconnect.FranceConnectLogoutCallback;
import oasis.web.authn.franceconnect.FranceConnectLogoutState;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.SoyTemplateBodyWriter;
import okhttp3.HttpUrl;

@RunWith(JukitoRunner.class)
public class LogoutPageFranceConnectTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LogoutPage.class);

      install(new SoyGuiceModule());
      install(new UrlsModule(ImmutableUrls.builder()
          .landingPage(URI.create("https://oasis/landing-page"))
          .build()));
      install(new FranceConnectModule(ImmutableFranceConnectModule.Settings.builder()
          .issuer("https://fcp/")
          .authorizationEndpoint(HttpUrl.parse("https://fcp/authorize"))
          .tokenEndpoint("https://fcp/token")
          .userinfoEndpoint("https://fcp/userinfo")
          .endSessionEndpoint(HttpUrl.parse("https://fcp/logout"))
          .clientId("fcp_client_id")
          .clientSecret("fcp_client_secret")
          .build()));

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());

      bindMock(SessionManagementHelper.class).in(TestSingleton.class);
    }
  }

  private static final String cookieName = CookieFactory.getCookieName(UserFilter.COOKIE_NAME, true);
  private static final String browserStateCookieName = CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, true);

  private static final UserAccount account = new UserAccount() {{
    setId("accountId");
    setNickname("Nickname");
    setLocale(ULocale.ROOT);
  }};

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
    setAccountId(account.getId());
    setFranceconnectIdToken("franceconnect_id_token");
  }};

  private static final AppInstance appInstance = new AppInstance() {{
    setId("appInstance");
    setName(new LocalizableString("Test Application Instance"));
  }};

  private static final String postLogoutRedirectUri = "https://application/after_logout";

  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUpMocks(SessionManagementHelper sessionManagementHelper, BrandRepository brandRepository) {
    when(sessionManagementHelper.generateBrowserState()).thenReturn("browser-state");
    when(brandRepository.getBrandInfo(any())).thenReturn(new BrandInfo());
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LogoutPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test
  public void testPost(TokenRepository tokenRepository, FranceConnectModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .request().post(Entity.form(new Form()
            .param("app_id", appInstance.getId())
            .param("post_logout_redirect_uri", postLogoutRedirectUri)
            .param("state", "some&state")));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);

    HttpUrl parsedLocation = HttpUrl.get(response.getLocation());
    assertThat(parsedLocation.newBuilder()
        .removeAllQueryParameters("id_token_hint")
        .removeAllQueryParameters("post_logout_redirect_uri")
        .removeAllQueryParameters("state")
        .build()).isEqualTo(settings.endSessionEndpoint());
    assertThat(parsedLocation.queryParameter("id_token_hint")).isEqualTo(sidToken.getFranceconnectIdToken());
    assertThat(parsedLocation.queryParameter("post_logout_redirect_uri"))
        .isEqualTo(resteasy.getBaseUriBuilder().path(FranceConnectLogoutCallback.class).build().toString());
    String fcState = parsedLocation.queryParameter("state");
    assertThat(fcState).isNotNull();

    assertThat(response.getCookies())
        .containsValue(FranceConnectLogoutState.createCookie(fcState, appInstance.getId(), postLogoutRedirectUri, "some&state", true))
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"))
        .containsKey(cookieName);
    assertThat(response.getCookies().get(cookieName).getExpiry()).isInThePast();

    verify(tokenRepository).revokeToken(sidToken.getId());
  }
}
