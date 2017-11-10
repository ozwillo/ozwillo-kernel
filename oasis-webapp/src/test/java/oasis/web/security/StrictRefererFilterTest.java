/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import java.net.URI;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.auth.AuthModule;
import oasis.http.testing.InProcessResteasy;
import oasis.services.security.OriginHelper;

@RunWith(Parameterized.class)
public class StrictRefererFilterTest {
  private static final String PORTAL_ORIGIN = "https://portal.ozwillo.com";

  private static final String KERNEL_REFERER = URI.create(InProcessResteasy.BASE_URI).resolve("/a/logout").toString();
  private static final String PORTAL_REFERER = PORTAL_ORIGIN + "/my/profile";
  private static final String OTHER_REFERER = "https://other/foo/bar";

  @Rule public InProcessResteasy resteasy = new InProcessResteasy(Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
      install(new NoopAuditLogModule());

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setPortalOrigin(PORTAL_ORIGIN)
          .build());
    }
  }));

  @Before
  public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(StrictRefererFeature.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResourceWithStrictReferer.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResourceWithStrictRefererAllowingPortal.class);
  }

  @Parameters(name = "{index}: path={0}, referer={1}, expected outcome={2}")
  public static Object[][] data() {
    return new Object[][] {
        // path,            referer,        expected outcome
        { "/open/open",     KERNEL_REFERER, "open"},
        { "/open/open",     PORTAL_REFERER, "open"},
        { "/open/open",     OTHER_REFERER,  "open"},
        { "/open/open",     null,           "open"},
        { "/open/closed",   KERNEL_REFERER, "closed"},
        { "/open/closed",   PORTAL_REFERER, "rejected"},
        { "/open/closed",   OTHER_REFERER,  "rejected"},
        { "/open/closed",   null,           "rejected"},
        { "/open/portal",   KERNEL_REFERER, "portal"},
        { "/open/portal",   PORTAL_REFERER, "portal"},
        { "/open/portal",   OTHER_REFERER,  "rejected"},
        { "/open/portal",   null,           "rejected"},
        { "/closed/open",   KERNEL_REFERER, "closed"},
        { "/closed/open",   PORTAL_REFERER, "rejected"},
        { "/closed/open",   OTHER_REFERER,  "rejected"},
        { "/closed/open",   null,           "rejected"},
        { "/closed/closed", KERNEL_REFERER, "closed"},
        { "/closed/closed", PORTAL_REFERER, "rejected"},
        { "/closed/closed", OTHER_REFERER,  "rejected"},
        { "/closed/closed", null,           "rejected"},
        { "/closed/portal", KERNEL_REFERER, "portal"},
        { "/closed/portal", PORTAL_REFERER, "portal"},
        { "/closed/portal", OTHER_REFERER,  "rejected"},
        { "/closed/portal", null,           "rejected"},
        { "/portal/open",   KERNEL_REFERER, "portal"},
        { "/portal/open",   PORTAL_REFERER, "portal"},
        { "/portal/open",   OTHER_REFERER,  "rejected"},
        { "/portal/open",   null,           "rejected"},
        { "/portal/closed", KERNEL_REFERER, "closed"},
        { "/portal/closed", PORTAL_REFERER, "rejected"},
        { "/portal/closed", OTHER_REFERER,  "rejected"},
        { "/portal/closed", null,           "rejected"},
        { "/portal/portal", KERNEL_REFERER, "portal"},
        { "/portal/portal", PORTAL_REFERER, "portal"},
        { "/portal/portal", OTHER_REFERER,  "rejected"},
        { "/portal/portal", null,           "rejected"},
    };
  }

  @Parameter(0) public String path;
  @Parameter(1) public String referer;
  @Parameter(2) public String expectedOutcome;

  @Test public void testWithNoHeader() {
    assumeTrue(referer == null);

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(path))
        .request()
        .post(Entity.text("foo"));

    if (expectedOutcome.equals("rejected")) {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    } else {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
      String payload = response.readEntity(String.class);
      assertThat(payload).isEqualTo(expectedOutcome);
    }
  }

  @Test public void testWithOrigin() {
    assumeNotNull(referer);

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(path))
        .request()
        .header(StrictRefererFilter.ORIGIN_HEADER, OriginHelper.originFromUri(referer))
        .post(Entity.text("foo"));

    if (expectedOutcome.equals("rejected")) {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    } else {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
      String payload = response.readEntity(String.class);
      assertThat(payload).isEqualTo(expectedOutcome);
    }
  }

  @Test public void testWithReferer() {
    assumeNotNull(referer);

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(path))
        .request()
        .header(StrictRefererFilter.REFERER_HEADER, referer)
        .post(Entity.text("foo"));

    if (expectedOutcome.equals("rejected")) {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    } else {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
      String payload = response.readEntity(String.class);
      assertThat(payload).isEqualTo(expectedOutcome);
    }
  }

  @Test public void testWithOriginAndReferer() {
    assumeNotNull(referer);

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(path))
        .request()
        .header(StrictRefererFilter.ORIGIN_HEADER, OriginHelper.originFromUri(referer))
        .header(StrictRefererFilter.REFERER_HEADER, referer)
        .post(Entity.text("foo"));

    if (expectedOutcome.equals("rejected")) {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
    } else {
      assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
      String payload = response.readEntity(String.class);
      assertThat(payload).isEqualTo(expectedOutcome);
    }
  }

  @Path("/open")
  @Produces(MediaType.TEXT_PLAIN)
  public static class DummyResource {
    @POST
    @Path("/open")
    public String open() {
      return "open";
    }

    @POST
    @Path("/closed")
    @StrictReferer
    public String closed() {
      return "closed";
    }

    @POST
    @Path("/portal")
    @StrictReferer(allowPortal = true)
    public String portal() {
      return "portal";
    }
  }

  @Path("/closed")
  @Produces(MediaType.TEXT_PLAIN)
  @StrictReferer
  public static class DummyResourceWithStrictReferer {
    @POST
    @Path("/open")
    public String openActuallyClosed() {
      return "closed";
    }

    @POST
    @Path("/closed")
    @StrictReferer
    public String closed() {
      return "closed";
    }

    @POST
    @Path("/portal")
    @StrictReferer(allowPortal = true)
    public String portal() {
      return "portal";
    }
  }

  @Path("/portal")
  @Produces(MediaType.TEXT_PLAIN)
  @StrictReferer(allowPortal = true)
  public static class DummyResourceWithStrictRefererAllowingPortal {
    @POST
    @Path("/open")
    public String openActuallyPortal() {
      return "portal";
    }

    @POST
    @Path("/closed")
    @StrictReferer
    public String closed() {
      return "closed";
    }

    @POST
    @Path("/portal")
    @StrictReferer(allowPortal = true)
    public String portal() {
      return "portal";
    }
  }
}