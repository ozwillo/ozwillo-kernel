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
        .target(UriBuilder.fromResource(DummyResource.class))
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
