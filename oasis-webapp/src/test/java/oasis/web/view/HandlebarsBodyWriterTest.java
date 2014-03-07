package oasis.web.view;

import static org.assertj.core.api.Assertions.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;

@RunWith(JukitoRunner.class)
public class HandlebarsBodyWriterTest {
  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(HandlebarsBodyWriter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testXss() {
    String response = resteasy.getClient().target("/xss").request().get(String.class);

    assertThat(response).doesNotContain("<script>alert");
  }

  @Test public void testXssViewJson() {
    String response = resteasy.getClient().target("/xssViaJson").request().get(String.class);

    assertThat(response)
        .doesNotContain("</script><script>alert")
        .doesNotContain("</script><!--pwnd");
  }

  @Path("/")
  public static class DummyResource {
    @GET
    @Path("/xss")
    @Produces(MediaType.TEXT_HTML)
    public View xss() {
      return new View(HandlebarsBodyWriterTest.class, "xss.html",
          ImmutableMap.of(
              "tentativeXss", "<script>alert('PWND!')</script>"
          ));
    }

    @GET
    @Path("/xssViaJson")
    @Produces(MediaType.TEXT_HTML)
    public View xssViaJson() {
      return new View(HandlebarsBodyWriterTest.class, "xssViaJson.html",
          ImmutableMap.of(
              "initData", ImmutableMap.of("tentativeXss", "</script><script>alert('PWND!');</script><!--pwnd")
          ));
    }
  }
}
