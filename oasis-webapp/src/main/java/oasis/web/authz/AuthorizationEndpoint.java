package oasis.web.authz;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import oasis.web.authn.Authenticated;
import oasis.web.authn.User;

@Path("/a/auth")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class AuthorizationEndpoint {

  private static final String CLIENT_ID = "client_id";
  private static final String REDIRECT_URI = "redirect_uri";
  private static final String STATE = "state";
  private static final String RESPONSE_TYPE = "response_type";
  private static final String SCOPE = "scope";

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();

  private MultivaluedMap<String, String> params;

  private StringBuilder redirect_uri;

  @Context
  private SecurityContext securityContext;

  @GET
  public Response get(@Context UriInfo uriInfo) {
    return post(uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response post(MultivaluedMap<String,String> params) {
    this.params = params;

    final String client_id = getRequiredParameter(CLIENT_ID);
    // TODO: check that client_id exists

    String redirectUri = getRequiredParameter(REDIRECT_URI);
    // Validate redirect_uri
    final URI ruri;
    try {
      ruri = new URI(redirectUri);
    } catch (URISyntaxException use) {
      throw invalidParam(REDIRECT_URI);
    }
    if (!ruri.isAbsolute() || ruri.isOpaque() || !Strings.isNullOrEmpty(ruri.getRawFragment())) {
      throw invalidParam(REDIRECT_URI);
    }
    if (!"http".equals(ruri.getScheme()) && !"https".equals(ruri.getScheme())) {
      throw invalidParam(REDIRECT_URI);
    }
    // TODO: check that redirect_uri matches client_id registration
    redirect_uri = new StringBuilder(redirectUri);

    // From now on, we can redirect to the client application, for both success and error conditions
    // Prepare the redirect_uri to end with a query-string so we can just append with '&' separators
    if (ruri.getRawQuery() == null) {
      redirect_uri.append('?');
    } else if (!ruri.getRawQuery().isEmpty()) {
      redirect_uri.append('&');
    }

    final String state = getParameter(STATE);
    if (state != null) {
      appendQueryParam(STATE, state);
    }

    final String response_type = getRequiredParameter(RESPONSE_TYPE);
    // TODO: support "implicit grant"
    if (!response_type.equals("code")) {
      throw error("unsupported_response_type", "Only 'code' is supported for now.");
    }

    final String scope = getRequiredParameter(SCOPE);
    List<String> scopes = SPACE_SPLITTER.splitToList(scope);
    // TODO: support scopes, for now only "openid" is supported
    if (!scopes.contains("openid")) {
      throw error("invalid_scope", "You must include 'openid'");
    }

    // TODO: OpenID Connect specifics

    String auth_code = securityContext.getUserPrincipal().getName() + ":" + scope; // TODO: generate auth_code
    appendQueryParam("code", auth_code);
    return Response.seeOther(URI.create(redirect_uri.toString())).build();
  }

  private void appendQueryParam(String paramName, String paramValue) {
    Escaper escaper = UrlEscapers.urlFormParameterEscaper();
    assert escaper.escape(paramName).equals(paramName) : "paramName needs escaping!";
    redirect_uri.append(paramName).append('=').append(escaper.escape(paramValue)).append('&');
  }

  private WebApplicationException invalidParam(String paramName) {
    return invalidRequest("Invalid parameter value: " + paramName);
  }

  private WebApplicationException invalidRequest(String message) {
    return error("invalid_request", message);
  }

  private WebApplicationException error(String error, @Nullable String description) {
    if (redirect_uri == null) {
      if (description != null) {
        error += ": " + description;
      }
      return new BadRequestException(error);
    }
    appendQueryParam("error", error);
    if (description != null) {
      appendQueryParam("error_description", description);
    }
    return new RedirectionException(Response.Status.SEE_OTHER, URI.create(redirect_uri.toString()));
  }

  /**
   * Returns a parameter value out of the parameters map.
   * <p>
   * Trims the value and normalizes the empty value to {@code null}.
   * <p>
   * If the parameter is included more than once, a {@link WebApplicationException} is thrown that will either display
   * the error to the user or redirect to the client application, depending on whether the {@link #redirect_uri} field
   * is {@code null} or not.
   *
   * @param paramName     the parameter name
   * @return the parameter (unique) value or {@code null} if absent or empty
   * @throws javax.ws.rs.WebApplicationException if the parameter is included more than once.
   */
  @Nullable
  private String getParameter(String paramName) {
    List<String> values = params.get(paramName);
    if (values == null || values.isEmpty()) {
      return null;
    }
    if (values.size() != 1) {
      throw tooManyValues(paramName);
    }
    String value = values.get(0);
    if (value != null) {
      value = value.trim();
      if (value.isEmpty()) {
        value = null;
      }
    }
    return value;
  }

  private WebApplicationException tooManyValues(String paramName) {
    return invalidRequest(paramName + " included more than once");
  }

  /**
   * Returns a required parameter value out of the parameter map.
   * <p>
   * The value is trimmed before being returned.
   * <p>
   * If the parameter is missing, has an empty value, or is included more than once, a {@link WebApplicationException}
   * is throw that will either display the error to the user or redirect to the client application, depending on
   * whether the {@link #redirect_uri} field is {@code null} or not.
   *
   * @param paramName     the parameter name
   * @return the parameter (unique) value (cannot be {@code null}
   * @throws javax.ws.rs.WebApplicationException if the parameter is absent, empty, or included more than once.
   */
  @Nonnull
  private String getRequiredParameter(String paramName) {
    String value = getParameter(paramName);
    if (value == null) {
      throw missingRequiredParameter(paramName);
    }
    return value;
  }

  private WebApplicationException missingRequiredParameter(String paramName) {
    return invalidRequest("Missing required parameter: " + paramName);
  }
}
