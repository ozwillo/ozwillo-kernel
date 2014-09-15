package oasis.web.authz;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.All;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;
import com.google.inject.Provides;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.model.i18n.LocalizableString;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.security.KeyPairLoader;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.web.authn.LoginPage;
import oasis.web.authn.testing.TestUserFilter;
import oasis.soy.SoyGuiceModule;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class AuthorizationEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(AuthorizationEndpoint.class);

      install(new SoyGuiceModule());

      bind(JsonFactory.class).to(JacksonFactory.class);
      bind(Clock.class).to(FixedClock.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(AppAdminHelper.class).in(TestSingleton.class);

      bindManyNamedInstances(String.class, "bad redirect_uri",
          "https://application/callback#hash",  // has #hash
          "ftp://application/callback",         // non-HTTP scheme
          "//application/callback",             // relative
          "data:text/plain,:foobar",            // opaque
          "https://attacker/callback"           // non-whitelisted
      );

      bind(OpenIdConnectModule.Settings.class).toInstance(OpenIdConnectModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());
    }

    @Provides FixedClock provideFixedClock() {
      return new FixedClock(now.getMillis());
    }
  }

  static final Instant now = new DateTime(2014, 7, 17, 14, 30).toInstant();

  private static final SidToken sidToken = new SidToken() {{
    setId("sidToken");
    setAccountId("accountId");
    setAuthenticationTime(now.minus(Duration.standardHours(1)));
  }};

  private static final AppInstance appInstance = new AppInstance() {{
    setId("appInstance");
    setName(new LocalizableString("Test Application Instance"));
    setProvider_id("organizationId");
  }};

  private static final Service service = new Service() {{
    setId("public-service");
    setName(new LocalizableString("Public Service"));
    setInstance_id(appInstance.getId());
    setProvider_id("organizationId");
    setVisible(true);
    // The redirect_uri contains %-encoded chars, which will need to be double-%-encoded in URLs.
    setRedirect_uris(Collections.singleton("https://application/callback?foo=bar%26baz"));
  }};
  private static final Service privateService = new Service() {{
    setId("private-service");
    setName(new LocalizableString("Private Service"));
    setInstance_id(appInstance.getId());
    setProvider_id("organizationId");
    setVisible(false);
    setRedirect_uris(Collections.singleton("https://application/callback?private"));
  }};

  // NOTE: scopes are supposed to have a title and description, we're indirectly
  // testing our resistance to missing data here by not setting them.
  private static final Scope openidScope = new Scope() {{
    setLocal_id("openid");
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
    setLocal_id("offline_access");
  }};

  private static final AuthorizationCode authorizationCode = new AuthorizationCode() {{
    setId("authCode");
    setAccountId(sidToken.getAccountId());
    setCreationTime(Instant.now());
    expiresIn(Duration.standardMinutes(10));
  }};

  // The state contains %-encoded chars, which will need to be double-%-encoded in URLs.
  private static final String state = "some=state&url=" + UrlEscapers.urlFormParameterEscaper().escape("/a?b=c&d=e");
  private static final String encodedState = UrlEscapers.urlFormParameterEscaper().escape(state);

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AuthorizationRepository authorizationRepository,
      AppInstanceRepository appInstanceRepository, ServiceRepository serviceRepository,
      ScopeRepository scopeRepository, TokenHandler tokenHandler) {
    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);
    when(serviceRepository.getServiceByRedirectUri(appInstance.getId(), Iterables.getOnlyElement(service.getRedirect_uris()))).thenReturn(service);
    when(serviceRepository.getServiceByRedirectUri(appInstance.getId(), Iterables.getOnlyElement(privateService.getRedirect_uris()))).thenReturn(privateService);

    when(scopeRepository.getScopes(anyCollectionOf(String.class))).thenAnswer(new Answer<Iterable<Scope>>() {
      @Override
      public Iterable<Scope> answer(InvocationOnMock invocation) throws Throwable {
        Collection<?> scopeIds = Sets.newHashSet((Collection<?>) invocation.getArguments()[0]);
        ArrayList<Scope> ret = new ArrayList<>(3);
        for (Scope scope : Arrays.asList(openidScope, authorizedScope, unauthorizedScope, offlineAccessScope)) {
          if (scopeIds.remove(scope.getId())) {
            ret.add(scope);
          }
        }
        // unknown scope:
        if (!scopeIds.isEmpty()) {
          throw new IllegalArgumentException();
        }
        return ret;
      }
    });
    when(scopeRepository.getScopes(Sets.newHashSet(openidScope.getId())))
        .thenReturn(singletonList(openidScope));
    when(scopeRepository.getScopes(Sets.newHashSet(openidScope.getId(), unauthorizedScope.getId())))
        .thenReturn(singletonList(openidScope));

    when(authorizationRepository.getAuthorizedScopes(sidToken.getAccountId(), appInstance.getId()))
        .thenReturn(new AuthorizedScopes() {{
          setScope_ids(Sets.newHashSet(openidScope.getId(), authorizedScope.getId()));
        }});

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createAuthorizationCode(eq(sidToken), anySetOf(String.class), eq(appInstance.getId()),
        anyString(), eq(Iterables.getOnlyElement(service.getRedirect_uris())), anyString())).thenReturn(authorizationCode);
    when(tokenHandler.createAuthorizationCode(eq(sidToken), anySetOf(String.class), eq(appInstance.getId()),
        anyString(), eq(Iterables.getOnlyElement(privateService.getRedirect_uris())), anyString())).thenReturn(authorizationCode);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(AuthorizationEndpoint.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @Test public void testNotLoggedIn() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToLogin(response);
  }

  @Test public void testTransparentRedirection(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(openidScope.getId()), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), "pass");
  }

  @Test public void testPromptUser() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertConsentPage(response);
  }

  /**
   * Same as {@link #testTransparentRedirection} except with {@code prompt=login}.
   * <p>Similar to {@link #testNotLoggedIn()} except user is logged-in but we force a login prompt.
   */
  @Test public void testPromptLogin() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "login")
        .request().get();

    assertRedirectToLogin(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code prompt=consent}. */
  @Test public void testPromptConsent() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "consent")
        .request().get();

    assertConsentPage(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code prompt=none} and no logged-in user. */
  @Test public void testPromptNone_loginRequired() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "login_required", null);
  }

  /** Same as {@link #testPromptUser()} except with {@code prompt=none}. */
  @Test public void testPromptNone_consentRequired() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, service, "consent_required", null);
  }

  @Test public void testUnknownClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "unknown")
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("access_denied");
  }

  @Test public void testMissingClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "client_id");
  }

  @Test public void testBadRedirectUri(@All("bad redirect_uri") String redirectUri) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(redirectUri))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testMissingRedirectUri() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testBadResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "foobar")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, service, "unsupported_response_type", null);
  }

  @Test public void testMissingResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, service, "invalid_request", "response_type");
  }

  /** Same as {@link #testTransparentRedirection} except with {@code response_mode=code}. */
  @Test public void testResponseMode() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("response_mode", "query")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response, service);
  }

  /**
   * Send error back to the application, as discussed
   * <a href="http://lists.openid.net/pipermail/openid-specs-ab/Week-of-Mon-20140317/004678.html">on the mailing list</a>
   */
  @Test public void testBadResponseMode() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("response_mode", "fragment")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, service, "invalid_request", "response_mode");
  }

  @Test public void testUnknownScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " unknown_scope")
        .request().get();

    assertRedirectError(response, service, "invalid_scope", null);
  }

  @Test public void testScopeMissingOpenid() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", authorizedScope.getId())
        .request().get();

    assertRedirectError(response, service, "invalid_scope", openidScope.getId());
  }

  @Test public void testMissingScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "scope");
  }

  @Test public void testPromptNoneAndValue() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "login none")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "prompt");
  }

  @Test public void testUnknownPromptValue() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "login unknown_value")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "prompt");
  }

  @Test public void testOfflineAccessIgnoredWithoutPromptConsent(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + offlineAccessScope.getId())
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(openidScope.getId()), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), "pass");
  }

  @Test public void testRequestParam() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("request", "whatever")
        .request().get();

    assertRedirectError(response, service, "request_not_supported", null);
  }

  @Test public void testRequestUriParam() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("request_uri", "whatever")
        .request().get();

    assertRedirectError(response, service, "request_uri_not_supported", null);
  }

  @Test public void testIdTokenHint(OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("id_token_hint", IdToken.signUsingRsaSha256(settings.keyPair.getPrivate(),
            jsonFactory,
            new JsonWebSignature.Header()
                .setType("JWS")
                .setAlgorithm("RS256"),
            new IdToken.Payload()
                .setIssuer(InProcessResteasy.BASE_URI.toString())
                .setSubject(sidToken.getAccountId())))
        .request().get();

    assertRedirectToApplication(response, service);
  }

  @Test public void testIdTokenHint_unparseableJwt() throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("id_token_hint", "not.a.jwt")
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_badSignature(JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("id_token_hint", IdToken.signUsingRsaSha256(
            KeyPairLoader.generateRandomKeyPair().getPrivate(),
            jsonFactory,
            new JsonWebSignature.Header()
                .setType("JWS")
                .setAlgorithm("RS256"),
            new IdToken.Payload()
                .setIssuer(InProcessResteasy.BASE_URI.toString())
                .setSubject(sidToken.getAccountId())))
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_badIssuer(OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("id_token_hint", IdToken.signUsingRsaSha256(
            settings.keyPair.getPrivate(),
            jsonFactory,
            new JsonWebSignature.Header()
                .setType("JWS")
                .setAlgorithm("RS256"),
            new IdToken.Payload()
                .setIssuer("https://invalid-issuer.example.com")
                .setSubject(sidToken.getAccountId())))
        .request().get();

    assertRedirectError(response, service, "invalid_request", "id_token_hint");
  }

  @Test public void testIdTokenHint_mismatchingSub(OpenIdConnectModule.Settings settings, JsonFactory jsonFactory) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("id_token_hint", IdToken.signUsingRsaSha256(
            settings.keyPair.getPrivate(),
            jsonFactory,
            new JsonWebSignature.Header()
                .setType("JWS")
                .setAlgorithm("RS256"),
            new IdToken.Payload()
                .setIssuer(InProcessResteasy.BASE_URI.toString())
                .setSubject("invalidSub")))
        .request().get();

    assertRedirectError(response, service, "login_required", null);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code max_age} that needs reauth. */
  @Test public void testMaxAge_needsReauth() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("max_age", "1000")
        .request().get();

    assertRedirectToLogin(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code max_age} OK. */
  @Test public void testMaxAge_ok(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("max_age", "4000")
        .request().get();

    assertRedirectToApplication(response, service);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(openidScope.getId()), appInstance.getId(), null,
        Iterables.getOnlyElement(service.getRedirect_uris()), "pass");
  }

  /** Same as {@link #testTransparentRedirection} except with a private service and app_admin user. */
  @Test public void testPrivateService_isAppAdmin(TokenHandler tokenHandler, AppAdminHelper appAdminHelper) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    when(appAdminHelper.isAdmin(sidToken.getAccountId(), appInstance)).thenReturn(true);

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response, privateService);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(openidScope.getId()), appInstance.getId(), null,
        Iterables.getOnlyElement(privateService.getRedirect_uris()), "pass");
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

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response, privateService);

    verify(tokenHandler).createAuthorizationCode(sidToken, ImmutableSet.of(openidScope.getId()), appInstance.getId(), null,
        Iterables.getOnlyElement(privateService.getRedirect_uris()), "pass");
  }

  /** Same as {@link #testTransparentRedirection} except with a private service and a user taht's neither an app_user or app_admin. */
  @Test public void testPrivateService_IsNeitherAppUserOrAppAdmin() throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", appInstance.getId())
        .queryParam("redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(privateService.getRedirect_uris())))
        .queryParam("state", encodedState)
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, privateService, "access_denied", "app_admin or app_user");
  }

  private void assertRedirectToApplication(Response response, Service service) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertRedirectUri(location, service);
    assertThat(location.getQueryParameters())
        .containsEntry("code", singletonList(TokenSerializer.serialize(authorizationCode, "pass")))
        .containsEntry("state", singletonList(state));
  }

  private void assertRedirectToLogin(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(LoginPage.class).build());

    UriInfo continueUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CONTINUE_PARAM)));
    assertThat(continueUrl.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(AuthorizationEndpoint.class).build());
    assertThat(continueUrl.getQueryParameters())
        .containsEntry("client_id", singletonList(appInstance.getId()))
        .containsEntry("redirect_uri", singletonList(Iterables.getOnlyElement(service.getRedirect_uris())))
        .containsEntry("state", singletonList(state))
        .containsEntry("response_type", singletonList("code"))
        .containsEntry("scope", singletonList(openidScope.getId()))
        .doesNotContainEntry("prompt", singletonList("login"));

    UriInfo cancelUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CANCEL_PARAM)));
    assertRedirectError(cancelUrl, service, "login_required", null);
  }

  private void assertConsentPage(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    // XXX: test the response (run the template?)
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
}
