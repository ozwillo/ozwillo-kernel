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
package oasis.web.utils;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ResponseFactory {
  /** As defined in <a href="https://tools.ietf.org/html/rfc4918#section-11.2">RFC 4918</a> */
  public static final int SC_UNPROCESSABLE_ENTITY = 422;
  /** As defined in <a href="http://tools.ietf.org/html/rfc6585#section-3">RFC 6585</a> */
  public static final int SC_PRECONDITION_REQUIRED = 428;

  public static final Response NOT_FOUND = Response.status(Response.Status.NOT_FOUND).build();
  public static final Response NO_CONTENT = Response.status(Response.Status.NO_CONTENT).build();

  public static Response conflict(String body) {
    return build(Response.Status.CONFLICT.getStatusCode(), body);
  }

  public static Response forbidden(String body) {
    return build(Response.Status.FORBIDDEN.getStatusCode(), body);
  }

  public static Response notFound(String body) {
    return build(Response.Status.NOT_FOUND.getStatusCode(), body);
  }

  public static Response preconditionFailed(String body) {
    return build(Response.Status.PRECONDITION_FAILED.getStatusCode(), body);
  }

  public static Response preconditionRequired(String body) {
    return build(SC_PRECONDITION_REQUIRED, body);
  }

  public static Response preconditionRequiredIfMatch() {
    return preconditionRequired("Missing If-Match");
  }

  public static Response unprocessableEntity(String body) {
    return build(SC_UNPROCESSABLE_ENTITY, body);
  }

  /** Creates a response with the given status and text/plain payload. */
  public static Response build(Response.Status status, String body) {
    return build(status.getStatusCode(), body);
  }

  /** Creates a response with the given status and text/plain payload. */
  public static Response build(int status, String body) {
    return Response
        .status(status)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity(body)
        .build();
  }
}
