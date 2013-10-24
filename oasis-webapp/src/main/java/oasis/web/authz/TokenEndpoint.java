package oasis.web.authz;

import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.common.base.Splitter;

import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;

@Path("/a/token")
@Authenticated @Client
public class TokenEndpoint {

  private static final Splitter CODE_SPLITTER = Splitter.on(':').limit(2);

  private MultivaluedMap<String, String> params;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response validate(MultivaluedMap<String, String> params) {
    this.params = params;

    String grant_type = getRequiredParameter("grant_type");
    if (!grant_type.equals("authorization_code")) {
      return errorResponse("unsupported_grant", null);
    }

    String code = getRequiredParameter("code");
    String redirect_uri = getRequiredParameter("redirect_uri");

    List<String> parts = CODE_SPLITTER.splitToList(code);
    if (parts.size() != 2) {
      return errorResponse("invalid_grant", null);
    }

    // TODO: validate auth code
    // TODO: generate access token
    String accessToken = parts.get(0) + ":" + System.currentTimeMillis(); // FIXME: bad for tests!
    // TODO: get scopes authorized by the user
    String scope = parts.get(1);

    // TODO: use IdTokenResponse
    TokenResponse response = new TokenResponse();
    response.setAccessToken(accessToken);
    response.setTokenType("Bearer");
    response.setExpiresInSeconds(3600L); // TODO: make configurable
    response.setScope(scope);

    return response(Response.Status.OK, response);
  }

  private Response response(Response.Status status, Object response) {
    return Response.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("Pragma", "no-cache")
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(response)
        .build();
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
    TokenErrorResponse response = new TokenErrorResponse();
    response.setError(error);
    response.setErrorDescription(description);
    return response(Response.Status.BAD_REQUEST, description);
  }
}
