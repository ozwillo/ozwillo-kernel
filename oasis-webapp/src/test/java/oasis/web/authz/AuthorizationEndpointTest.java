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
package oasis.web.authz;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jukito.All;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.ScopesAndClaims;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.AuthorizationContextClasses;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.model.i18n.LocalizableString;
import oasis.security.KeyPairLoader;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.services.branding.BrandHelper;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.UrlsModule;
import oasis.web.authn.ClientCertificateHelper;
import oasis.web.authn.ClientCertificateHelper.ClientCertificateData;
import oasis.web.authn.ImmutableClientCertificateHelper;
import oasis.web.authn.LoginPage;
import oasis.web.authn.SessionManagementHelper;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class AuthorizationEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(AuthorizationEndpoint.class);

      install(new SoyGuiceModule());
      install(new UrlsModule(ImmutableUrls.builder().build()));

      bind(Clock.class).toInstance(Clock.fixed(now, zone));

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(AppAdminHelper.class).in(TestSingleton.class);
      bindMock(SessionManagementHelper.class).in(TestSingleton.class);
      bindMock(ClientCertificateHelper.class).in(TestSingleton.class);

      bindManyNamedInstances(String.class, "bad redirect_uri",
          "https://application/callback#hash",  // has #hash
          "ftp://application/callback",         // non-HTTP scheme
          "//application/callback",             // relative
          "data:text/plain,:foobar",            // opaque
          "https://attacker/callback"           // non-whitelisted
      );

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .setEnableClientCertificates(true)
          .build());
    }
  }

  static final String browserStateCookieName = CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, true);

  private static final ZoneId zone = ZoneId.of("Europe/Paris");

  static final Instant now = ZonedDateTime.of(2014, 7, 17, 14, 30, 0, 0, zone).toInstant();

  private static final UserAccount account = new UserAccount() {{
    setId("accountId");
    setNickname("Nickname");
    setLocale(ULocale.ROOT);
  }};

  private static final SidToken sidToken = new SidToken() {{
    setId("sidToken");
    setAccountId(account.getId());
    setAuthenticationTime(now.minus(Duration.ofHours(1)));
  }};
  private static final SidToken sidTokenUsingClientCertificate = new SidToken() {{
    setId("sidToken");
    setAccountId(account.getId());
    setAuthenticationTime(now.minus(Duration.ofHours(1)));
    setUsingClientCertificate(true);
  }};

  private static final ClientCertificate someClientCertificate = new ClientCertificate() {{
    setId("some certificate");
    setSubject_dn("valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id(account.getId());
  }};
  private static final ClientCertificate serviceClientCertificate = new ClientCertificate() {{
    setId("service certificate");
    setSubject_dn("service subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.PROVIDER);
    setClient_id("service");
  }};

  private static final ClientCertificateData someClientCertificateData =
      ImmutableClientCertificateHelper.ClientCertificateData.of(someClientCertificate.getSubject_dn(), someClientCertificate.getIssuer_dn());
  private static final ClientCertificateData serviceClientCertificateData =
      ImmutableClientCertificateHelper.ClientCertificateData.of(serviceClientCertificate.getSubject_dn(), serviceClientCertificate.getIssuer_dn());

  private static final AppInstance appInstance = new AppInstance() {{
    setId("appInstance");
    setName(new LocalizableString("Test Application Instance"));
    setProvider_id("organizationId");
    setStatus(InstantiationStatus.RUNNING);
  }};
  private static final AppInstance stoppedAppInstance = new AppInstance() {{
    setId("stoppedAppInstance");
    setName(new LocalizableString("Test Stopped Application Instance"));
    setProvider_id("organizationId");
    setStatus(InstantiationStatus.STOPPED);
  }};

  private static final Service service = new Service() {{
    setId("public-service");
    setName(new LocalizableString("Public Service"));
    setInstance_id(appInstance.getId());
    setProvider_id("organizationId");
    setAccess_control(AccessControl.ANYONE);
    // The redirect_uri contains %-encoded chars, which will need to be double-%-encoded in URLs.
    setRedirect_uris(Collections.singleton("https://application/callback?foo=bar%26baz"));
  }};
  private static final Service privateService = new Service() {{
    setId("private-service");
    setName(new LocalizableString("Private Service"));
    setInstance_id(appInstance.getId());
    setProvider_id("organizationId");
    setAccess_control(AccessControl.RESTRICTED);
    setRedirect_uris(Collections.singleton("https://application/callback?private"));
  }};

  // NOTE: scopes are supposed to have a title and description, we're indirectly
  // testing our resistance to missing data here by not setting them.
  private static final Scope openidScope = new Scope() {{
    setLocal_id(Scopes.OPENID);
  }};
  private static final Scope profileScope = new Scope() {{
    setLocal_id(Scopes.PROFILE);
  }};
  private static final Scope authorizedScope = new Scope() {{
    setInstance_id("other-app");
    setLocal_id("authorized");
  }};
  private static final Scope unauthorizedScope = new Scope() {{
    setInstance_id("other-app");
    setLocal_id("unauthorized");
  }};
  private static final Scope offlineAccessScope = new Scope() {{
    setLocal_id(Scopes.OFFLINE_ACCESS);
  }};

  private static final AuthorizationCode authorizationCode = new AuthorizationCode() {{
    setId("authCode");
    setAccountId(sidToken.getAccountId());
    setCreationTime(Instant.now());
    expiresIn(Duration.ofMinutes(10));
  }};

  private static final AppInstance portalAppInstance = new AppInstance() {{
    setId(ClientIds.PORTAL);
    setName(new LocalizableString("Test Application Instance"));
    setProvider_id("organizationId");
    for (String scopeId : new String[] { Scopes.OPENID, Scopes.PROFILE}) {
      NeededScope neededScope = new NeededScope();
      neededScope.setScope_id(scopeId);
      getNeeded_scopes().add(neededScope);
    }
    setStatus(InstantiationStatus.RUNNING);
  }};
  private static final Service portalService = new Service() {{
    setId("portal-service");
    setName(new LocalizableString("Portal Service"));
    setInstance_id(portalAppInstance.getId());
    setProvider_id("organizationId");
    setVisibility(Visibility.HIDDEN);
    setAccess_control(AccessControl.ANYONE);
    setRedirect_uris(Collections.singleton("https://portal/callback"));
  }};

  // The state contains %-encoded chars, which will need to be double-%-encoded in URLs.
  private static final String state = "some=state&url=" + UrlEscapers.urlFormParameterEscaper().escape("/a?b=c&d=e");
  private static final String encodedState = UrlEscapers.urlFormParameterEscaper().escape(state);

  private static final String codeChallenge = "let.this.test.string.be.a.valid.code.challenge";

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AccountRepository accountRepository, AuthorizationRepository authorizationRepository,
      AppInstanceRepository appInstanceRepository, ServiceRepository serviceRepository,
      ScopeRepository scopeRepository, TokenHandler tokenHandler, SessionManagementHelper sessionManagementHelper,
      ClientCertificateRepository clientCertificateRepository, BrandRepository brandRepository) {
    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);

    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);
    when(appInstanceRepository.getAppInstance(stoppedAppInstance.getId())).thenReturn(stoppedAppInstance);
    when(serviceRepository.getServiceByRedirectUri(appInstance.getId(), Iterables.getOnlyElement(service.getRedirect_uris()))).thenReturn(service);
    when(serviceRepository.getServiceByRedirectUri(appInstance.getId(), Iterables.getOnlyElement(privateService.getRedirect_uris()))).thenReturn(privateService);

    when(scopeRepository.getScopes(anyCollection())).thenAnswer(invocation -> {
      Collection<?> scopeIds = new HashSet<>((Collection<?>) invocation.getArguments()[0]);
      ArrayList<Scope> ret = new ArrayList<>(3);
      for (Scope scope : Arrays.asList(openidScope, profileScope, authorizedScope, unauthorizedScope, offlineAccessScope)) {
        if (scopeIds.remove(scope.getId())) {
          ret.add(scope);
        }
      }
      // unknown scope:
      if (!scopeIds.isEmpty()) {
        throw new IllegalArgumentException();
      }
      return ret;
    });

    when(authorizationRepository.getAuthorizedScopes(sidToken.getAccountId(), appInstance.getId()))
        .thenReturn(new AuthorizedScopes() {{
          setScope_ids(ImmutableSet.of(Scopes.OPENID, authorizedScope.getId()));
          setClaim_names(ImmutableSet.of("nickname", "name"));
        }});

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createAuthorizationCode(eq(sidToken), anySet(), anySet(), eq(appInstance.getId()),
        or(isNull(), anyString()), eq(Iterables.getOnlyElement(service.getRedirect_uris())), or(isNull(), anyString()), anyString())).thenReturn(authorizationCode);
    when(tokenHandler.createAuthorizationCode(eq(sidToken), anySet(), anySet(), eq(appInstance.getId()),
        or(isNull(), anyString()), eq(Iterables.getOnlyElement(privateService.getRedirect_uris())), or(isNull(), anyString()), anyString())).thenReturn(authorizationCode);
    when(tokenHandler.createAuthorizationCode(eq(sidTokenUsingClientCertificate), anySet(), anySet(), eq(appInstance.getId()),
        or(isNull(), anyString()), eq(Iterables.getOnlyElement(service.getRedirect_uris())), or(isNull(), anyString()), anyString())).thenReturn(authorizationCode);

    // Portal special-case
    when(appInstanceRepository.getAppInstance(portalAppInstance.getId())).thenReturn(portalAppInstance);
    when(serviceRepository.getServiceByRedirectUri(portalAppInstance.getId(), Iterables.getOnlyElement(portalService.getRedirect_uris()))).thenReturn(portalService);
    when(tokenHandler.createAuthorizationCode(eq(sidToken), anySet(), anySet(), eq(portalAppInstance.getId()),
        or(isNull(), anyString()), eq(Iterables.getOnlyElement(portalService.getRedirect_uris())), or(isNull(), anyString()), anyString())).thenReturn(authorizationCode);

    when(sessionManagementHelper.generateBrowserState()).thenReturn("browser-state");
    when(sessionManagementHelper.computeSessionState(appInstance.getId(), Iterables.getOnlyElement(service.getRedirect_uris()), "browser-state"))
        .thenReturn("session state");
    when(sessionManagementHelper.computeSessionState(appInstance.getId(), Iterables.getOnlyElement(privateService.getRedirect_uris()), "browser-state"))
        .thenReturn("session state");
    when(sessionManagementHelper.computeSessionState(portalAppInstance.getId(), Iterables.getOnlyElement(portalService.getRedirect_uris()), "browser-state"))
        .thenReturn("session state");

    when(clientCertificateRepository.getClientCertificate(someClientCertificate.getSubject_dn(), someClientCertificate.getIssuer_dn()))
        .thenReturn(someClientCertificate);
    when(clientCertificateRepository.getClientCertificate(serviceClientCertificate.getSubject_dn(), serviceClientCertificate.getIssuer_dn()))
        .thenReturn(serviceClientCertificate);
    // TODO: check for various cases where user has certificates or not; for now simply avoid an NPE by returning an empty iterable.
    when(clientCertificateRepository.getClientCertificatesForClient(ClientType.USER, account.getId())).thenReturn(Collections.emptyList());

    when(brandRepository.getBrandInfo(any())).thenReturn(new BrandInfo());
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(AuthorizationEndpoint.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test public void testNotLoggedIn() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectToLogin(response);
  }

  @Test public void testTransparentRedirection(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectToApplication(response, service);
    assertThat(response.getCookies())
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  @Test public void testTransparentRedirection_withClaims(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"name\":null}}"))
        .request().get();

    assertRedirectToApplication(response, service);
    assertThat(response.getCookies())
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of("name"), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  @Test public void testTransparentRedirection_withClaims_essential(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"nickname\":{\"essential\":true}}}"))
        .request().get();

    assertRedirectToApplication(response, service);
    assertThat(response.getCookies())
        .containsEntry(browserStateCookieName, SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of("nickname"), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  @Test public void testSessionState(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request()
        .cookie(SessionManagementHelper.createBrowserStateCookie(true, "browser-state"))
        .get();

    assertRedirectToApplication(response, service);
    assertThat(response.getCookies()).doesNotContainKey(SessionManagementHelper.COOKIE_NAME);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  @Test public void testCodeChallenge(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), codeChallenge, "pass");
  }

  @Test public void testCodeChallenge_missingCodeChallengeMethod() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", codeChallenge)
        .request().get();

    assertRedirectError(response, service, "invalid_request", "code_challenge_method");
  }

  @Test public void testCodeChallenge_badCodeChallengeMethod() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "unknown")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "code_challenge_method");
  }

  @Test public void testCodeChallenge_codeChallengeTooShort() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", "too-short")
        .queryParam("code_challenge_method", "S256")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "code_challenge");
  }

  @Test public void testCodeChallenge_codeChallengeTooLong() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", Strings.repeat(codeChallenge, 4))
        .queryParam("code_challenge_method", "S256")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "code_challenge");
  }

  @Test public void testCodeChallenge_invalidCodeChallenge() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("code_challenge", codeChallenge.replace('.', '/'))
        .queryParam("code_challenge_method", "S256")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "code_challenge");
  }

  @Test public void testPromptUser() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + unauthorizedScope.getId())
        .request().get();

    assertConsentPage(response)
        .matches(hiddenInput("scope", unauthorizedScope.getId()));
  }

  @Test public void testPromptUser_fromClaims_unauthorized() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"locale\":null}}"))
        .request().get();

    assertConsentPage(response)
        .matches(hiddenInput("claim", "locale"));
  }

  @Test public void testPromptUser_fromClaims_essentialAndMissing() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"name\":{\"essential\":true}}}"))
        .request().get();

    assertConsentPage(response)
        .matches(hiddenInput("claim", "name"));
  }

  @Test public void testAutomaticallyAuthorizePortalsNeededScopes(AuthorizationRepository authorizationRepository, TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", portalAppInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(portalService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + Scopes.PROFILE)
        .request().get();

    assertRedirectToApplication(response, portalService);

    verify(authorizationRepository).authorize(sidToken.getAccountId(), portalAppInstance.getId(),
        ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE),
        Scopes.mapScopesToClaims(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE)).collect(ImmutableSet.toImmutableSet()));
    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE),
        Scopes.mapScopesToClaims(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE)).collect(ImmutableSet.toImmutableSet()),
        portalAppInstance.getId(), null, Iterables.getOnlyElement(portalService.getRedirect_uris()), null, "pass");
  }

  @Test public void testPromptForPortalForNonNeededScopes(AuthorizationRepository authorizationRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", portalAppInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(portalService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + unauthorizedScope.getId())
        .request().get();

    assertConsentPage(response)
        .matches(hiddenInput("scope", unauthorizedScope.getId()));

    // Verify that we still automatically authorize portal's needed scopes
    verify(authorizationRepository).authorize(sidToken.getAccountId(), portalAppInstance.getId(),
        ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE),
        Scopes.mapScopesToClaims(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE)).collect(ImmutableSet.toImmutableSet()));
  }

  /** This is the same as {@link #testAutomaticallyAuthorizePortalsNeededScopes} except with prompt=consent. */
  @Test public void testPromptForPortalIfPromptConsent(AuthorizationRepository authorizationRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", portalAppInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(portalService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + Scopes.PROFILE)
        .queryParam("prompt", "consent")
        .request().get();

    AbstractCharSequenceAssert<?, String> consentPage = assertConsentPage(response);
    Scopes.mapScopesToClaims(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE))
        .forEach(claimName -> consentPage.matches(hiddenInput("claim", claimName)));

    // Verify that we still automatically authorize portal's needed scopes
    verify(authorizationRepository).authorize(sidToken.getAccountId(), portalAppInstance.getId(),
        ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE),
        Scopes.mapScopesToClaims(ImmutableSet.of(Scopes.OPENID, Scopes.PROFILE)).collect(ImmutableSet.toImmutableSet()));
  }

  /**
   * Same as {@link #testTransparentRedirection} except with {@code prompt=login}.
   * <p>Similar to {@link #testNotLoggedIn()} except user is logged-in but we force a login prompt.
   */
  @Test public void testPromptLogin() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("prompt", "login")
        .request().get();

    assertRedirectToLogin(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code prompt=consent}. */
  @Test public void testPromptConsent() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("prompt", "consent")
        .request().get();

    assertConsentPage(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code prompt=none} and no logged-in user. */
  @Test public void testPromptNone_loginRequired() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "login_required", null);
  }

  /** Same as {@link #testPromptUser()} except with {@code prompt=none}. */
  @Test public void testPromptNone_consentRequired() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + unauthorizedScope.getId())
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "consent_required", null);
  }

  /** Same as {@link #testPromptUser_fromClaims_unauthorized()} except with {@code prompt=none}. */
  @Test public void testPromptNone_consentRequired_fromClaims_unauthorized() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + unauthorizedScope.getId())
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"locale\":null}}"))
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "consent_required", null);
  }

  /** Same as {@link #testPromptUser_fromClaims_essentialAndMissing()} except with {@code prompt=none}. */
  @Test public void testPromptNone_consentRequired_fromClaims_essentialAndMissing() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + unauthorizedScope.getId())
        .queryParam("claims", UrlEscapers.urlFormParameterEscaper().escape("{\"userinfo\":{\"name\":{\"essential\":true}}}"))
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "consent_required", null);
  }

  /** Same as {@link #testPromptUser()} except with acr_values asking for certificate (and no certificate presented). */
  @SuppressWarnings("unchecked")
  @Test public void testAskForCertificate_notUsingCertificate(ClientCertificateRepository clientCertificateRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("acr_values", "ignored " + AuthorizationContextClasses.EIDAS_LOW + " " + AuthorizationContextClasses.EIDAS_SUBSTANTIAL + " foo bar")
        .request().get();

    assertConsentPage(response);
    // TODO: assert page prompts for certificate
  }

  /** Same as {@link #testTransparentRedirection(TokenHandler)} except with acr_values asking for certificate (and certificate presented). */
  @SuppressWarnings("unchecked")
  @Test public void testAskForCertificate_usingCertificate(ClientCertificateHelper clientCertificateHelper) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidTokenUsingClientCertificate));

    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(someClientCertificateData);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("acr_values", "ignored " + AuthorizationContextClasses.EIDAS_LOW + " " + AuthorizationContextClasses.EIDAS_SUBSTANTIAL + " foo bar")
        .request().get();

    assertRedirectToApplication(response, service);
  }

  /** Same as {@link #testAskForCertificate_usingCertificate(ClientCertificateHelper)} except the certificate is linked to another account. */
  @SuppressWarnings("unchecked")
  @Test public void testAskForCertificate_usingCertificateLinkedToOtherAccount(ClientCertificateHelper clientCertificateHelper) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(serviceClientCertificateData);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("acr_values", "ignored " + AuthorizationContextClasses.EIDAS_LOW + " " + AuthorizationContextClasses.EIDAS_SUBSTANTIAL + " foo bar")
        .request().get();

    assertConsentPage(response);
    // TODO: assert page prompts for certificate
  }

  @Test public void testStoppedClient() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", stoppedAppInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("access_denied");
  }

  @Test public void testUnknownClient() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", "unknown")
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("access_denied");
  }

  @Test public void testMissingClient() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "client_id");
  }

  @Test public void testBadRedirectUri(@All("bad redirect_uri") String redirectUri) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(redirectUri))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testMissingRedirectUri() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testBadResponseType() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "foobar")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectError(response, service, "unsupported_response_type", null);
  }

  @Test public void testMissingResponseType() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectError(response, service, "invalid_request", "response_type");
  }

  /** Same as {@link #testTransparentRedirection} except with {@code response_mode=code}. */
  @Test public void testResponseMode() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("response_mode", "query")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectToApplication(response, service);
  }

  /**
   * Send error back to the application, as discussed
   * <a href="http://lists.openid.net/pipermail/openid-specs-ab/Week-of-Mon-20140317/004678.html">on the mailing list</a>
   */
  @Test public void testBadResponseMode() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("response_mode", "fragment")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectError(response, service, "invalid_request", "response_mode");
  }

  @Test public void testUnknownScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " unknown_scope")
        .request().get();

    assertRedirectError(response, service, "invalid_scope", null);
  }

  @Test public void testScopeMissingOpenid() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", authorizedScope.getId())
        .request().get();

    assertRedirectError(response, service, "invalid_scope", Scopes.OPENID);
  }

  @Test public void testMissingScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "scope");
  }

  @Test public void testPromptNoneAndValue() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("prompt", "login none")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "prompt");
  }

  @Test public void testUnknownPromptValue() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("prompt", "login unknown_value")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "prompt");
  }

  @Test public void testOfflineAccessIgnoredWithoutPromptConsent(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID + " " + Scopes.OFFLINE_ACCESS)
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  @Test public void testRequestParam() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("request", "whatever")
        .request().get();

    assertRedirectError(response, service, "request_not_supported", null);
  }

  @Test public void testRequestUriParam() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("request_uri", "whatever")
        .request().get();

    assertRedirectError(response, service, "request_uri_not_supported", null);
  }

  @Test public void testIdTokenHint(AuthModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("id_token_hint", idToken)
        .request().get();

    assertRedirectToApplication(response, service);
  }

  @Test public void testIdTokenHint_unparseableJwt() throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("id_token_hint", "not.a.jwt")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_badSignature() throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(KeyPairLoader.generateRandomKeyPair().getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("id_token_hint", idToken)
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_badIssuer(AuthModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));


    JwtClaims claims = new JwtClaims();
    claims.setIssuer("https://invalid-issuer.example.com");
    claims.setSubject(sidToken.getAccountId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("id_token_hint", idToken)
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_mismatchingSub(AuthModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));


    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject("invalidSub");
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("id_token_hint", idToken)
        .request().get();

    assertRedirectError(response, service, "login_required", null);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code max_age} that needs reauth. */
  @Test public void testMaxAge_needsReauth() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("max_age", "1000")
        .request().get();

    assertRedirectToLogin(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code max_age} OK. */
  @Test public void testMaxAge_ok(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .queryParam("max_age", "4000")
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), null, "pass");
  }

  /** Same as {@link #testTransparentRedirection} except with a private service and app_admin user. */
  @Test public void testPrivateService_isAppAdmin(TokenHandler tokenHandler, AppAdminHelper appAdminHelper) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    when(appAdminHelper.isAdmin(sidToken.getAccountId(), appInstance)).thenReturn(true);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectToApplication(response, privateService);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(privateService.getRedirect_uris()), null, "pass");
  }

  /** Same as {@link #testTransparentRedirection} except with a private service and app_user user. */
  @Test public void testPrivateService_isAppUser(TokenHandler tokenHandler, AccessControlRepository accessControlRepository) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    when(accessControlRepository.getAccessControlEntry(privateService.getInstance_id(), sidToken.getAccountId()))
        .thenReturn(new AccessControlEntry() {{
          setId("membership");
          setInstance_id(privateService.getInstance_id());
          setUser_id(sidToken.getAccountId());
        }});

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectToApplication(response, privateService);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(Scopes.OPENID), ImmutableSet.of(), appInstance.getId(), null,
        Iterables.getOnlyElement(privateService.getRedirect_uris()), null, "pass");
  }

  /** Same as {@link #testTransparentRedirection} except with a private service and a user taht's neither an app_user or app_admin. */
  @Test public void testPrivateService_IsNeitherAppUserOrAppAdmin() throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", Scopes.OPENID)
        .request().get();

    assertRedirectError(response, privateService, "access_denied", "app_admin or app_user");
  }

  private void assertRedirectToApplication(Response response, Service service) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertRedirectUri(location, service);
    assertThat(location.getQueryParameters())
        .containsEntry("code", singletonList(TokenSerializer.serialize(authorizationCode, "pass")))
        .containsEntry("state", singletonList(state))
        .containsEntry("session_state", singletonList("session state"));
  }

  private void assertRedirectToLogin(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(resteasy.getBaseUriBuilder().path(LoginPage.class).build());
    assertThat(location.getQueryParameters().getFirst(BrandHelper.BRAND_PARAM)).isEqualTo(BrandInfo.DEFAULT_BRAND);

    UriInfo continueUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CONTINUE_PARAM)));
    assertThat(continueUrl.getAbsolutePath()).isEqualTo(resteasy.getBaseUriBuilder().path(AuthorizationEndpoint.class).build());
    assertThat(continueUrl.getQueryParameters())
        .containsEntry("client_id", singletonList(appInstance.getId()))
        .containsEntry("redirect_uri", singletonList(Iterables.getOnlyElement(service.getRedirect_uris())))
        .containsEntry("state", singletonList(state))
        .containsEntry("response_type", singletonList("code"))
        .containsEntry("scope", singletonList(Scopes.OPENID))
        .doesNotContainEntry("prompt", singletonList("login"));

    UriInfo cancelUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CANCEL_PARAM)));
    assertRedirectError(cancelUrl, service, "login_required", null);
  }

  private AbstractCharSequenceAssert<?, String> assertConsentPage(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    return assertThat(response.readEntity(String.class))
        .matches(hiddenInput("scope", Scopes.OPENID));
  }

  private void assertErrorNoRedirect(Response response, String error, String errorDescription) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class))
        .contains(error)
        .contains(errorDescription);
  }

  private void assertRedirectError(Response response, Service service, String error, @Nullable String errorDescription) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertRedirectError(location, service, error, errorDescription);
  }

  private void assertRedirectError(UriInfo location, Service service, String error, @Nullable String errorDescription) {
    assertRedirectUri(location, service);
    assertThat(location.getQueryParameters())
        .containsEntry("error", singletonList(error))
        .containsEntry("state", singletonList(state));
    if (errorDescription != null) {
      assertThat(location.getQueryParameters().getFirst("error_description")).contains(errorDescription);
    }
  }

  private void assertRedirectUri(UriInfo location, Service service) {
    URI redirect_uri = URI.create(Iterables.getOnlyElement(service.getRedirect_uris()));
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(redirect_uri).replaceQuery(null).build());
    for (Map.Entry<String, List<String>> entry : new ResteasyUriInfo(redirect_uri).getQueryParameters().entrySet()) {
      assertThat(location.getQueryParameters()).containsEntry(entry.getKey(), entry.getValue());
    }
  }

  private String hiddenInput(String name, @Nullable String value) {
    return "(?s).*<input[^>]+type=([\"']?)hidden\\1[^>]+name=([\"']?)"
        + Pattern.quote(HtmlEscapers.htmlEscaper().escape(name))
        + "(\\2)[^>]+value=([\"']?)"
        + (value == null ? "[^\"]*" : Pattern.quote(HtmlEscapers.htmlEscaper().escape(value)))
        + "\\3[\\s>].*";
  }
}
