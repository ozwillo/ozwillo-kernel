package oasis.web.auditlog;

import static com.google.common.base.Predicates.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.HttpAuditLogEvent;

@Provider
@Priority(Integer.MIN_VALUE)
public class HttpInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String START_TIME_PROP = HttpInterceptor.class.getName() + ".startTime";

  private static final ImmutableSet<String> HTTP_HEADERS_TO_LOG = ImmutableSet.of(
      HttpHeaders.ACCEPT,
      HttpHeaders.ACCEPT_CHARSET,
      HttpHeaders.ACCEPT_ENCODING,
      HttpHeaders.ACCEPT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE,
      HttpHeaders.CONTENT_LENGTH,
      HttpHeaders.USER_AGENT,
      "Origin",
      "Referer"
  );

  @Inject AuditLogService auditLogService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setProperty(START_TIME_PROP, System.nanoTime());
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    Long startTimeNanos = (Long) requestContext.getProperty(START_TIME_PROP);
    Long elapsedNanos = null;
    Long startTimeMillis = null;
    Long endTimeMillis = System.currentTimeMillis();
    if (startTimeNanos != null) {
      elapsedNanos = System.nanoTime() - startTimeNanos;
      startTimeMillis = endTimeMillis - TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }

    Map<String, List<String>> headers = Maps.filterKeys(requestContext.getHeaders(), in(HTTP_HEADERS_TO_LOG));

    auditLogService.event(HttpAuditLogEvent.class)
        .setUrl(requestContext.getUriInfo().getRequestUri().toString())
        .setMethod(requestContext.getMethod())
        .setHeaders(ImmutableMap.<String, Object>copyOf(headers))
        .setStartTime(startTimeMillis)
        .setEndTime(endTimeMillis)
        .setElapsedTime(elapsedNanos)
        .setStatus(responseContext.getStatus())
        .setAuthenticationScheme(requestContext.getSecurityContext().getAuthenticationScheme())
        .setAuthenticatedUser(requestContext.getSecurityContext().getUserPrincipal() != null
            ? requestContext.getSecurityContext().getUserPrincipal().getName() : null)
        .log();
  }
}
