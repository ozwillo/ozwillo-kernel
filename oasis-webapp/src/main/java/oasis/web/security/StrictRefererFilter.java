package oasis.web.security;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.services.security.OriginHelper;

@Provider
@StrictReferer
public class StrictRefererFilter implements ContainerRequestFilter {
  private static final String REFERER_HEADER = "Referer";
  private static final String ORIGIN_HEADER = "Origin";

  @Inject AuditLogService auditLogService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String origin = requestContext.getHeaderString(ORIGIN_HEADER);
    String expectedOrigin = OriginHelper.originFromUri(requestContext.getUriInfo().getRequestUri().toString());
    boolean valid = false;
    if (origin != null) {
      valid = expectedOrigin.equals(origin);

      if (!valid) {
        auditLogService.event(StrictRefererErrorLogEvent.class)
            .setEndpoint(requestContext.getUriInfo().getPath())
            .setActualOrigin(origin)
            .setExpectedOrigin(expectedOrigin)
            .setFrom(StrictRefererErrorLogEvent.OriginFrom.ORIGIN)
            .log();
      }
    } else {
      String referer = requestContext.getHeaderString(REFERER_HEADER);
      if (referer != null) {
        String originFromReferer = OriginHelper.originFromUri(referer);
        valid = expectedOrigin.equals(originFromReferer);

        if (!valid) {
          auditLogService.event(StrictRefererErrorLogEvent.class)
              .setEndpoint(requestContext.getUriInfo().getPath())
              .setActualOrigin(originFromReferer)
              .setExpectedOrigin(expectedOrigin)
              .setFrom(StrictRefererErrorLogEvent.OriginFrom.REFERER)
              .log();
        }
      } else {
        auditLogService.event(StrictRefererErrorLogEvent.class)
            .setEndpoint(requestContext.getUriInfo().getPath())
            .setExpectedOrigin(expectedOrigin)
            .log();
      }
    }
    if (!valid) {
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
    }
  }

  public static class StrictRefererErrorLogEvent extends AuditLogEvent {
    public StrictRefererErrorLogEvent() {
      super("strict_referer_event");
    }

    public StrictRefererErrorLogEvent setActualOrigin(String actualOrigin) {
      this.addContextData("actual_origin", actualOrigin);
      return this;
    }

    public StrictRefererErrorLogEvent setExpectedOrigin(String expectedOrigin) {
      this.addContextData("expected_origin", expectedOrigin);
      return this;
    }

    public StrictRefererErrorLogEvent setEndpoint(String endpoint) {
      this.addContextData("endpoint", endpoint);
      return this;
    }

    public StrictRefererErrorLogEvent setFrom(OriginFrom from) {
      this.addContextData("from", from);
      return this;
    }

    public static enum OriginFrom {
      ORIGIN, REFERER
    }
  }
}
