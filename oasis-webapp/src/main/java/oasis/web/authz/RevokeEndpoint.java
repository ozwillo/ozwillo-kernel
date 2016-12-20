/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.web.authz;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import oasis.model.authn.AbstractOAuthToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.openidconnect.ErrorResponse;

@Path("/a/revoke")
@Authenticated
@Client
public class RevokeEndpoint {
  private MultivaluedMap<String, String> params;

  @Context SecurityContext securityContext;

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response post(MultivaluedMap<String, String> params) {
    this.params = params;

    String token_serial = getRequiredParameter("token");

    AbstractOAuthToken token = tokenHandler.getCheckedToken(token_serial, AbstractOAuthToken.class);

    if (token == null) {
      // From RFC 7009:
      //     Note: invalid tokens do not cause an error response since the client
      //     cannot handle such an error in a reasonable way.  Moreover, the
      //     purpose of the revocation request, invalidating the particular token,
      //     is already achieved.
      return Response.ok().build();
    }

    String client_id = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();
    if (!token.getServiceProviderId().equals(client_id)) {
      return errorResponse("unauthorized_client", null);
    }

    tokenRepository.revokeToken(token.getId());

    return Response.ok().build();
  }

  private String getRequiredParameter(String paramName) {
    List<String> values = params.get(paramName);
    if (values == null || values.isEmpty()) {
      throw missingRequiredParameter(paramName);
    }
    if (values.size() != 1) {
      throw tooManyValues(paramName);
    }
    String value = values.get(0);
    if (value == null) {
      throw missingRequiredParameter(paramName);
    }
    value = value.trim();
    if (value.isEmpty()) {
      throw missingRequiredParameter(paramName);
    }
    return value;
  }

  private WebApplicationException missingRequiredParameter(String paramName) {
    return invalidRequest("Missing required parameter: " + paramName);
  }

  private WebApplicationException tooManyValues(String paramName) {
    return invalidRequest(paramName + " included more than once");
  }

  private WebApplicationException invalidRequest(String message) {
    return error("invalid_request", message);
  }

  private WebApplicationException error(String error, @Nullable String description) {
    return new BadRequestException(errorResponse(error, description));
  }

  private Response errorResponse(String error, @Nullable String description) {
    ErrorResponse response = new ErrorResponse()
        .setError(error)
        .setError_description(description);
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(response)
        .build();
  }
}
