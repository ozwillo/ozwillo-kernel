package oasis.web.utils;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ResponseFactory {
  public static final int SC_UNPROCESSABLE_ENTITY = 422;
  public static final int SC_PRECONDITION_REQUIRED = 428;

  public static final Response NOT_FOUND = Response.status(Response.Status.NOT_FOUND).build();
  public static final Response NO_CONTENT = Response.status(Response.Status.NO_CONTENT).build();

  public static final Response notFound(String body) {
    return build(Response.Status.NOT_FOUND.getStatusCode(), body);
  }

  public static final Response preconditionFailed(String body) {
    return build(Response.Status.PRECONDITION_FAILED.getStatusCode(), body);
  }

  public static final Response preconditionRequired(String body) {
    return build(SC_PRECONDITION_REQUIRED, body);
  }

  public static final Response preconditionRequiredIfMatch() {
    return preconditionRequired("Missing If-Match");
  }

  public static final Response unprocessableEntity(String body) {
    return build(SC_UNPROCESSABLE_ENTITY, body);
  }

  private static Response build(int status, String body) {
    return Response
        .status(status)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity(body)
        .build();
  }

}
