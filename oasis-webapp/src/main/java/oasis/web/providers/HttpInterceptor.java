package oasis.web.providers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import com.google.common.collect.ImmutableMap;

import oasis.audit.AuditService;
import oasis.audit.HttpLogEvent;

@Provider
public class HttpInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

  @Inject
  private AuditService auditService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setProperty("startTime", System.nanoTime());
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    Long startTime = (Long) requestContext.getProperty("startTime");
    Long endTime = null;
    Long elapsed = null;
    if (startTime != null) {
      endTime = System.nanoTime();
      elapsed = endTime - startTime;
    }

    ImmutableMap.Builder<String, Object> headersMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, List<String>> entry : requestContext.getHeaders().entrySet()) {
      headersMapBuilder.put(entry.getKey(), entry.getValue());
    }

    auditService.event(HttpLogEvent.class)
        .setUrl(requestContext.getUriInfo().getRequestUri().toString())
        .setMethod(requestContext.getMethod())
        .setHeaders(headersMapBuilder.build())
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setElapsedTime(elapsed)
        .setStatus(responseContext.getStatus())
        .log();
  }
}
