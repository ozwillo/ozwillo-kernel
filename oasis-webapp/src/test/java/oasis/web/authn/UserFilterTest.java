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
package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.cookies.CookieFactory;
import oasis.web.utils.UserAgentFingerprinter;

@RunWith(JukitoRunner.class)
public class UserFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserFilter.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(UserAgentFingerprinter.class).in(TestSingleton.class);
      bindMock(SessionManagementHelper.class).in(TestSingleton.class);
      bindMock(ClientCertificateHelper.class).in(TestSingleton.class);
    }
  }

  static final String cookieName = CookieFactory.getCookieName(UserFilter.COOKIE_NAME, true);
  static final String browserStateCookieName = CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, true);

  static final Instant now = Instant.now();

  static final SidToken validSidToken = new SidToken();
  static {
    validSidToken.setId("validSession");
    validSidToken.setAccountId("userAccount");
    validSidToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validSidToken.setExpirationTime(now.plus(Duration.standardHours(1)));
    validSidToken.setUserAgentFingerprint("fingerprint".getBytes(StandardCharsets.UTF_8));
  }
  static final SidToken validSidTokenUsingCertificate = new SidToken();
  static {
    validSidTokenUsingCertificate.setId(validSidToken.getId());
    validSidTokenUsingCertificate.setAccountId(validSidToken.getAccountId());
    validSidTokenUsingCertificate.setCreationTime(validSidToken.getCreationTime());
    validSidTokenUsingCertificate.setExpirationTime(validSidToken.getExpirationTime());
    validSidTokenUsingCertificate.setUserAgentFingerprint(validSidToken.getUserAgentFingerprint());
    validSidTokenUsingCertificate.setUsingClientCertificate(true);
  }
  static final ClientCertificate userCertificate = new ClientCertificate() {{
    setSubject_dn("valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id(validSidToken.getAccountId());
  }};
  static final ClientCertificate otherUserCertificate = new ClientCertificate() {{
    setSubject_dn("other valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id("other user account");
  }};
  static final ClientCertificate serviceCertificate = new ClientCertificate() {{
    setSubject_dn("service subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.PROVIDER);
    setClient_id("client_id");
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Inject UserAgentFingerprinter fingerprinter;
  @Inject TokenRepository tokenRepository;
  @Inject SessionManagementHelper sessionManagementHelper;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", SidToken.class)).thenReturn(validSidToken);
    when(tokenHandler.getCheckedToken("validUsingCertificate", SidToken.class)).thenReturn(validSidTokenUsingCertificate);
    when(tokenHandler.getCheckedToken("invalid", SidToken.class)).thenReturn(null);

    when(tokenRepository.renewSidToken(validSidToken.getId(), false)).thenReturn(validSidToken);
    when(tokenRepository.renewSidToken(validSidToken.getId(), true)).thenReturn(validSidTokenUsingCertificate);

    when(sessionManagementHelper.generateBrowserState()).thenReturn("browser-state");
  }

  @After public void verifyMocks() {
    verify(sessionManagementHelper, never()).computeSessionState(anyString(), anyString(), anyString());
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testNoCookie(TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request().get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies())
        .doesNotContainKey(cookieName)
        .containsKeys(browserStateCookieName);
    assertThat(response.getCookies().get(browserStateCookieName)).isEqualTo(
        SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));
    assertThat(response.readEntity(SidToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler, tokenRepository);
  }

  @SuppressWarnings("unchecked")
  @Test public void testNoCookieWithCertificate(TokenHandler tokenHandler, ClientCertificateHelper clientCertificateHelper) {
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(userCertificate);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies())
        .doesNotContainKey(cookieName)
        .containsKeys(browserStateCookieName);
    assertThat(response.getCookies().get(browserStateCookieName)).isEqualTo(
        SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));
    assertThat(response.readEntity(SidToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler, tokenRepository);
  }

  @Test public void testBrowserStateOnly(TokenHandler tokenHandler) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(browserStateCookieName, "browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertThat(response.readEntity(SidToken.class)).isNull();

    verifyNoMoreInteractions(tokenHandler, tokenRepository);
  }

  @Test public void testAuthenticated() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .cookie(browserStateCookieName, "browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), false);
  }

  @SuppressWarnings("unchecked")
  @Test public void testAuthenticatedWithCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(userCertificate);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .cookie(browserStateCookieName, "old-browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies())
        .doesNotContainKeys(cookieName)
        .containsKeys(browserStateCookieName);
    assertThat(response.getCookies().get(browserStateCookieName)).isEqualTo(
        SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidTokenUsingCertificate);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), true);
    verify(sessionManagementHelper).generateBrowserState();
  }

  @SuppressWarnings("unchecked")
  @Test public void testAuthenticatedWithoutCertificate() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "validUsingCertificate")
        .cookie(browserStateCookieName, "old-browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies())
        .doesNotContainKeys(cookieName)
        .containsKeys(browserStateCookieName);
    assertThat(response.getCookies().get(browserStateCookieName)).isEqualTo(
        SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), false);
    verify(sessionManagementHelper).generateBrowserState();
  }

  @SuppressWarnings("unchecked")
  @Test public void testAuthenticatedWithMismatchingCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(otherUserCertificate);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .cookie(browserStateCookieName, "browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), false);
  }

  @SuppressWarnings("unchecked")
  @Test public void testAuthenticatedWithServiceCertificate(ClientCertificateHelper clientCertificateHelper) {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());
    when(clientCertificateHelper.getClientCertificate(any(MultivaluedMap.class))).thenReturn(serviceCertificate);

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .cookie(browserStateCookieName, "browser-state")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies()).doesNotContainKeys(cookieName, browserStateCookieName);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), false);
  }

  @Test public void testAuthenticatedNoBrowserState() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn(validSidToken.getUserAgentFingerprint());

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getCookies())
        .doesNotContainKey(cookieName)
        .containsKey(browserStateCookieName);
    assertThat(response.getCookies().get(browserStateCookieName)).isEqualTo(
        SessionManagementHelper.createBrowserStateCookie(true, "browser-state"));
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);

    verify(tokenRepository).renewSidToken(validSidToken.getId(), false);
  }

  @Test public void testWithInvalidCookie() {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "invalid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).containsKey(cookieName);
    assertThat(response.getCookies().get(cookieName).getExpiry()).isInThePast();
    assertThat(response.readEntity(SidToken.class)).isNull();

    verify(tokenRepository, never()).renewSidToken(eq(validSidToken.getId()), anyBoolean());
  }

  @Test public void testWithInvalidFingerprint() {
    when(fingerprinter.fingerprint(any(ContainerRequestContext.class))).thenReturn("attacker".getBytes(StandardCharsets.UTF_8));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(DummyResource.class).build()).request()
        .cookie(cookieName, "valid")
        .get();

    commonAssertions(response);
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NO_CONTENT);
    assertThat(response.getCookies()).containsKey(cookieName);
    assertThat(response.getCookies().get(cookieName).getExpiry()).isInThePast();
    assertThat(response.readEntity(SidToken.class)).isNull();

    verify(tokenRepository, never()).renewSidToken(eq(validSidToken.getId()), anyBoolean());
  }

  private void commonAssertions(Response response) {
    assertThat(response.getHeaders()).containsKeys(HttpHeaders.VARY, HttpHeaders.CACHE_CONTROL);
    assertThat(response.getHeaderString(HttpHeaders.VARY).split("\\s*,\\s*")).contains(HttpHeaders.COOKIE);
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL).split("\\s*,\\s*")).contains("private");
  }

  @Path("/")
  @User
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SidToken get() {
      final UserSessionPrincipal principal = (UserSessionPrincipal) securityContext.getUserPrincipal();
      return principal == null ? null : principal.getSidToken();
    }
  }
}
