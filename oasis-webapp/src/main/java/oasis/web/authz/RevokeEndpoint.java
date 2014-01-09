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

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.authn.AbstractOAuthToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Path("/a/revoke")
@Authenticated
@Client
@Api(value = "/a/revoke", description = "Revoke Endpoint.")
public class RevokeEndpoint {
  private MultivaluedMap<String, String> params;

  @Context SecurityContext securityContext;

  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @ApiOperation(
      value = "Revoke a token",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc7009\">OAuth 2.0 Token Revocation RFC</a> for more information."
  )
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
    if (params == null) { // Workaround for https://issues.jboss.org/browse/RESTEASY-1004
      throw missingRequiredParameter(paramName);
    }
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
    TokenErrorResponse response = new TokenErrorResponse()
        .setError(error)
        .setErrorDescription(description);
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(response)
        .build();
  }
}
