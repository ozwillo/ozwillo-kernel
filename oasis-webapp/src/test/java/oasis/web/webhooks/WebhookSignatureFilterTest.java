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
package oasis.web.webhooks;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import junit.framework.TestCase;
import oasis.http.testing.InProcessResteasy;

@RunWith(JukitoRunner.class)
public class WebhookSignatureFilterTest extends TestCase {

  private static final String SECRET = "This is a secret";
  private static final byte[] PAYLOAD = "This is the request payload".getBytes(StandardCharsets.UTF_8);
  private static final String SIGNATURE = "sha1=3daba1f18d85905076a8ed72caf13565ece571fb";

  @Inject @Rule public InProcessResteasy resteasy;

  @Before
  public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testWebhookSignature() {
    Response response = resteasy.getClient()
        .register(new WebhookSignatureFilter(SECRET))
        .target(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(DummyResource.class))
        .request()
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    String signature = response.readEntity(String.class);
    assertThat(signature)
        .startsWith("sha1=") // the prefix is case-sensitive
        .isEqualToIgnoringCase(SIGNATURE);
  }

  @Path("/")
  public static class DummyResource {
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    public String echoSignature(@Context HttpHeaders headers, byte[] payload) {
      assertThat(payload).isEqualTo(PAYLOAD);
      return headers.getHeaderString(WebhookSignatureFilter.HEADER);
    }
  }
}
