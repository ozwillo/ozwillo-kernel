package oasis.web.security;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import oasis.services.security.OriginHelper;

@Provider
@StrictReferer
public class StrictRefererFilter implements ContainerRequestFilter {
  private static final String REFERER_HEADER = "Referer";
  private static final String ORIGIN_HEADER = "Origin";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String origin = requestContext.getHeaderString(ORIGIN_HEADER);

    String expectedOrigin = OriginHelper.originFromUri(requestContext.getUriInfo().getBaseUri().toString());
    boolean valid = false;
    if (origin != null) {
      valid = expectedOrigin.equals(origin);
    } else {
      String referer = requestContext.getHeaderString(REFERER_HEADER);
      if (referer != null) {
        String originFromReferer = OriginHelper.originFromUri(referer);
        valid = expectedOrigin.equals(originFromReferer);
      }
    }
    if (!valid) {
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
    }
  }
}
