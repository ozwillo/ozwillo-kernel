package oasis.web.authz;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

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

import com.google.api.client.testing.http.FixedClock;
import com.google.api.client.util.Clock;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provides;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.Scope;
import oasis.model.applications.ServiceProvider;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.LoginPage;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.HandlebarsBodyWriter;

@RunWith(JukitoRunner.class)
public class AuthorizationEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(AuthorizationEndpoint.class);

      bind(Clock.class).to(FixedClock.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);

      bindManyNamedInstances(String.class, "bad redirect_uri",
          "https://application/callback#hash",  // contains has #hash
          "ftp://application/callback",         // non-HTTP scheme
          "//application/callback",             // relative
          "data:text/plain,:foobar"             // opaque
      );
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

  private static final ServiceProvider serviceProvider = new ServiceProvider() {{
    setId("application");
    setName("Application");
  }};

  private static final Scope openidScope = new Scope() {{
    setId("openid");
  }};
  private static final Scope authorizedScope = new Scope() {{
    setId("authorized");
  }};
  private static final Scope unauthorizedScope = new Scope() {{
    setId("unauthorized");
  }};

  private static final AuthorizationCode authorizationCode = new AuthorizationCode() {{
    setId("authCode");
    setAccountId(sidToken.getAccountId());
    setCreationTime(Instant.now());
    expiresIn(Duration.standardMinutes(10));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AuthorizationRepository authorizationRepository,
      ApplicationRepository applicationRepository, TokenHandler tokenHandler) {
    when(applicationRepository.getServiceProvider(serviceProvider.getId())).thenReturn(serviceProvider);

    when(authorizationRepository.getScopes(anySetOf(String.class))).thenAnswer(new Answer<Iterable<Scope>>() {
      @Override
      public Iterable<Scope> answer(InvocationOnMock invocation) throws Throwable {
        Set<?> scopeIds = Sets.newHashSet((Set<?>) invocation.getArguments()[0]);
        ArrayList<Scope> ret = new ArrayList<>(3);
        for (Scope scope : Arrays.asList(openidScope, authorizedScope, unauthorizedScope)) {
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
    when(authorizationRepository.getScopes(Sets.newHashSet(openidScope.getId())))
        .thenReturn(singletonList(openidScope));
    when(authorizationRepository.getScopes(Sets.newHashSet(openidScope.getId(), unauthorizedScope.getId())))
        .thenReturn(singletonList(openidScope));

    when(authorizationRepository.getAuthorizedScopes(sidToken.getAccountId(), serviceProvider.getId()))
        .thenReturn(new AuthorizedScopes() {{
          setScopeIds(Sets.newHashSet(openidScope.getId(), authorizedScope.getId()));
        }});

    when(tokenHandler.generateRandom()).thenReturn("pass");
    when(tokenHandler.createAuthorizationCode(eq(sidToken.getAccountId()), anySetOf(String.class), eq(serviceProvider.getId()),
        anyString(), eq("https://application/callback"), anyBoolean(), anyString())).thenReturn(authorizationCode);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(AuthorizationEndpoint.class);
    resteasy.getDeployment().getProviderFactory().register(HandlebarsBodyWriter.class);
  }

  @Test public void testNotLoggedIn() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToLogin(response);
  }

  @Test public void testTransparentRedirection(TokenHandler tokenHandler) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response);

    verify(tokenHandler).createAuthorizationCode(sidToken.getAccountId(), ImmutableSet.of(openidScope.getId()), serviceProvider.getId(), null,
        "https://application/callback", false, "pass");
  }

  @Test public void testPromptUser() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
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
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
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
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "consent")
        .request().get();

    assertConsentPage(response);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code prompt=none} and no logged-in user. */
  @Test public void testPromptNone_loginRequired() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, "login_required", null);
  }

  /** Same as {@link #testPromptUser()} except with {@code prompt=none}. */
  @Test public void testPromptNone_consentRequired() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .queryParam("prompt", "none")
        .request().get();

    assertRedirectError(response, "consent_required", null);
  }

  @Test public void testUnknownClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "unknown")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("access_denied");
  }

  @Test public void testMissingClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "client_id");
  }

  @Test public void testBadRedirectUri(@All("bad redirect_uri") String redirectUri) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", redirectUri)
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testMissingRedirectUri() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testBadResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "foobar")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, "unsupported_response_type", null);
  }

  @Test public void testMissingResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, "invalid_request", "response_type");
  }

  /** Same as {@link #testTransparentRedirection} except with {@code response_mode=code}. */
  @Test public void testResponseMode() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("response_mode", "query")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectToApplication(response);
  }

  /**
   * Send error back to the application, as discussed
   * <a href="http://lists.openid.net/pipermail/openid-specs-ab/Week-of-Mon-20140317/004678.html">on the mailing list</a>
   */
  @Test public void testBadResponseMode() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("response_mode", "fragment")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertRedirectError(response, "invalid_request", "response_mode");
  }

  @Test public void testUnknownScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " unknown_scope")
        .request().get();

    assertRedirectError(response, "invalid_scope", null);
  }

  @Test public void testScopeMissingOpenid() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", authorizedScope.getId())
        .request().get();

    assertRedirectError(response, "invalid_scope", openidScope.getId());
  }

  @Test public void testMissingScope() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .request().get();

    assertRedirectError(response, "invalid_request", "scope");
  }

  @Test public void testPromptNoneAndValue() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "login none")
        .request().get();

    assertRedirectError(response, "invalid_request", "prompt");
  }

  @Test public void testUnknownPromptValue() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("prompt", "login unknown_value")
        .request().get();

    assertRedirectError(response, "invalid_request", "prompt");
  }

  @Test public void testRequestParam() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("request", "whatever")
        .request().get();

    assertRedirectError(response, "request_not_supported", null);
  }

  @Test public void testRequestUriParam() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("request_uri", "whatever")
        .request().get();

    assertRedirectError(response, "request_uri_not_supported", null);
  }

  /** Same as {@link #testTransparentRedirection} except with {@code max_age} that needs reauth. */
  @Test public void testMaxAge_needsReauth() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
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
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .queryParam("max_age", "4000")
        .request().get();

    assertRedirectToApplication(response);

    verify(tokenHandler).createAuthorizationCode(sidToken.getAccountId(), ImmutableSet.of(openidScope.getId()), serviceProvider.getId(), null,
        "https://application/callback", true, "pass");
  }

  private void assertRedirectToApplication(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(URI.create("https://application/callback"));
    assertThat(location.getQueryParameters())
        .containsEntry("code", singletonList(TokenSerializer.serialize(authorizationCode, "pass")))
        .containsEntry("state", singletonList("state"));
  }

  private void assertRedirectToLogin(Response response) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(LoginPage.class).build());

    UriInfo continueUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CONTINUE_PARAM)));
    assertThat(continueUrl.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(AuthorizationEndpoint.class).build());
    assertThat(continueUrl.getQueryParameters())
        .containsEntry("client_id", singletonList("application"))
        .containsEntry("redirect_uri", singletonList("https://application/callback"))
        .containsEntry("state", singletonList("state"))
        .containsEntry("response_type", singletonList("code"))
        .containsEntry("scope", singletonList(openidScope.getId()))
        .doesNotContainEntry("prompt", singletonList("login"));

    UriInfo cancelUrl = new ResteasyUriInfo(URI.create(location.getQueryParameters().getFirst(LoginPage.CANCEL_PARAM)));
    assertRedirectError(cancelUrl, "login_required", null);
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

  private void assertRedirectError(Response response, String error, @Nullable String errorDescription) {
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertRedirectError(location, error, errorDescription);
  }

  private void assertRedirectError(UriInfo location, String error, String errorDescription) {
    assertThat(location.getAbsolutePath()).isEqualTo(URI.create("https://application/callback"));
    assertThat(location.getQueryParameters())
        .containsEntry("error", singletonList(error))
        .containsEntry("state", singletonList("state"));
    if (errorDescription != null) {
      assertThat(location.getQueryParameters().getFirst("error_description")).contains(errorDescription);
    }
  }
}
