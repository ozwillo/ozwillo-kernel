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
import java.util.regex.Pattern;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.ClientType;
import oasis.services.authn.CredentialsService;

@RunWith(JukitoRunner.class)
public class ClientAuthenticationFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(ClientAuthenticationFilter.class);

      bindMock(CredentialsService.class).in(TestSingleton.class);
    }
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(CredentialsService credentialsService) {
    when(credentialsService.checkPassword(ClientType.PROVIDER, "invalid_id", "invalid:secret")).thenReturn(false);
    when(credentialsService.checkPassword(ClientType.PROVIDER, "valid_id", "valid:secret")).thenReturn(true);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(ClientAuthenticationFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test
  public void testWithoutAuthorizationHeader() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE)).matches(Pattern.compile("Basic .*", Pattern.CASE_INSENSITIVE));
  }

  @Test
  public void testIncorrectAuthScheme() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("Whatever", "valid_id", "valid:secret"))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
  }

  @Test
  public void testMissingCredentials() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic ")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test
  public void testTooManyCredentials() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("Basic", "valid_id", "valid:secret") + " too many values")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test
  public void testMalformedBase64() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic €")
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test
  public void testMalformedUTF8Base64Decoding() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode(new byte[] {(byte) 0x80}))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test
  public void testWithMalformedCredentials() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("valid_id".getBytes(StandardCharsets.UTF_8)))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test
  public void testWithEmptyClientId() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("Basic", "", "secret"))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE)).isNotNull();
  }

  @Test
  public void testWithInvalidCredentials() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("Basic", "invalid_id", "invalid:secret"))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
    assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE)).isNotNull();
  }

  @Test
  public void testWithValidCredentials() {
    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(DummyResource.class).build())
        .request()
        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader("Basic", "valid_id", "valid:secret"))
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo("valid_id");
  }

  private String getAuthorizationHeader(String scheme, String clientId, String clientSecret) {
    return scheme + " " + BaseEncoding.base64().encode((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
  }

  @Path("/")
  @Authenticated @Client
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
      return ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    }
  }
}
