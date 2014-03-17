package oasis.http.fixes;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Workaround https://issues.jboss.org/browse/RESTEASY-1006.
 */
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {
  private static final Logger logger = LoggerFactory.getLogger(UnhandledExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
    if (exception instanceof WebApplicationException) {
      Response response = ((WebApplicationException) exception).getResponse();
      if (response != null) {
        return response;
      }
    }

    logger.error("Unhandled exception: {}", exception.getMessage(), exception);

    return Response.serverError().build();
  }
}