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

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.Scope;
import oasis.model.applications.ServiceProvider;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.services.authn.TokenHandler;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.HandlebarsBodyWriter;

@RunWith(JukitoRunner.class)
public class AuthorizationEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(AuthorizationEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);

      bindManyNamedInstances(String.class, "bad redirect_uri",
          "https://application/callback#hash",  // contains has #hash
          "ftp://application/callback",         // non-HTTP scheme
          "//application/callback",             // relative
          "data:text/plain,:foobar"             // opaque
      );
    }
  }

  private static final SidToken sidToken = new SidToken() {{
    setId("sidToken");
    setAccountId("accountId");
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
        anyString(), eq("https://application/callback"), anyString())).thenReturn(authorizationCode);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(AuthorizationEndpoint.class);
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));
    resteasy.getDeployment().getProviderFactory().register(HandlebarsBodyWriter.class);
  }

  @Test public void testTransparentRedirection() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId())
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(URI.create("https://application/callback"));
    assertThat(location.getQueryParameters())
        .containsKey("code")
        .containsEntry("state", singletonList("state"));
  }

  @Test public void testPromptUser() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    // XXX: test the response (run the template?)
  }

  @Test public void testUnknownClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "unknown")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    assertThat(response.readEntity(String.class)).contains("access_denied");
  }

  @Test public void testMissingClient() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "client_id");
  }

  @Test public void testBadRedirectUri(@All("bad redirect_uri") String redirectUri) {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", redirectUri)
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testMissingRedirectUri() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertErrorNoRedirect(response, "invalid_request", "redirect_uri");
  }

  @Test public void testBadResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "foobar")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertRedirectError(response, "unsupported_response_type", null);
  }

  @Test public void testMissingResponseType() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("scope", openidScope.getId() + " " + unauthorizedScope.getId())
        .request().get();

    assertRedirectError(response, "invalid_request", "response_type");
  }

  @Test public void testUnknownScope() {
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
    Response response = resteasy.getClient().target(UriBuilder.fromResource(AuthorizationEndpoint.class))
        .queryParam("client_id", "application")
        .queryParam("redirect_uri", "https://application/callback")
        .queryParam("state", "state")
        .queryParam("response_type", "code")
        .request().get();

    assertRedirectError(response, "invalid_request", "scope");
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
    assertThat(location.getAbsolutePath()).isEqualTo(URI.create("https://application/callback"));
    assertThat(location.getQueryParameters())
        .containsEntry("error", singletonList(error))
        .containsEntry("state", singletonList("state"));
    if (errorDescription != null) {
      assertThat(location.getQueryParameters().getFirst("error_description")).contains(errorDescription);
    }
  }
}
