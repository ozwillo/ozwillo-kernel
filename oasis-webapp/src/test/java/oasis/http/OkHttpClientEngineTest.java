/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
package oasis.http;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import oasis.web.webhooks.WebhookSignatureFilter;

@RunWith(JukitoRunner.class)
public class OkHttpClientEngineTest {

  private static final String SECRET = "This is a secret";
  private static final byte[] PAYLOAD = "This is the request payload".getBytes(StandardCharsets.UTF_8);
  private static final String SIGNATURE = "sha1=3daba1f18d85905076a8ed72caf13565ece571fb";

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      install(new HttpClientModule());
    }
  }

  // In case an error is thrown in the MockWebServer, so clients don't block infinitely.
  @Rule public Timeout timeout = Timeout.seconds(10);

  @Rule public MockWebServer mockServer = new MockWebServer();
  @Before public void setUp() {
    mockServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        switch (request.getPath()) {
          case "/simple":
            return new MockResponse()
                .setResponseCode(Response.Status.NOT_FOUND.getStatusCode())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .setHeader("X-Whatever", "some header")
                .setBody("Not found");
          case "/writerInterceptor":
            assertThat(request.getBody().readByteArray()).isEqualTo(PAYLOAD);
            // fall-through
          case "/requestHeader":
            return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .setBody(request.getHeader(WebhookSignatureFilter.HEADER));
        }
        throw new AssertionError("Unexpected request: " + request);
      }
    });
  }

  @Inject Client client;

  @Test public void simpleRequest() {
    Response response = client.target(mockServer.url("/simple").toString()).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NOT_FOUND);
    assertThat(response.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
    assertThat(response.getHeaderString("X-Whatever")).isEqualTo("some header");
    assertThat(response.readEntity(String.class)).isEqualTo("Not found");
  }

  @Test public void requestHeader() {
    Response response = client.target(mockServer.url("/requestHeader").toString()).request()
        .header(WebhookSignatureFilter.HEADER, SIGNATURE)
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    String signature = response.readEntity(String.class);
    assertThat(signature).isEqualTo(SIGNATURE);
  }

  @Test public void simpleWriterInterceptors() {
    Response response = client.target(mockServer.url("/writerInterceptor").toString())
        .register(new WebhookSignatureFilter(SECRET))
        .request()
        .post(Entity.json(PAYLOAD));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    String signature = response.readEntity(String.class);
    assertThat(signature)
        .startsWith("sha1=") // the prefix is case-sensitive
        .isEqualToIgnoringCase(SIGNATURE);
  }
}
