package oasis.web.authz;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.authn.AuthorizationCode;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.Scope;
import oasis.model.applications.ScopeCardinality;
import oasis.model.applications.ServiceProvider;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.AccountPrincipal;
import oasis.web.authn.Authenticated;
import oasis.web.authn.User;
import oasis.web.view.View;

@Path("/a/auth")
@Authenticated
@User
@Produces(MediaType.TEXT_HTML)
@Api(value = "/a/auth", description = "Authorization Endpoint.")
public class AuthorizationEndpoint {
  private static final String APPROVE_PATH = "/approve";

  private static final String CLIENT_ID = "client_id";
  private static final String REDIRECT_URI = "redirect_uri";
  private static final String STATE = "state";
  private static final String NONCE = "nonce";
  private static final String RESPONSE_TYPE = "response_type";
  private static final String SCOPE = "scope";

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @Inject AuthorizationRepository authorizationRepository;
  @Inject ApplicationRepository applicationRepository;
  @Inject TokenHandler tokenHandler;

  private MultivaluedMap<String, String> params;
  private StringBuilder redirectUriBuilder;

  @GET
  @ApiOperation(
      value = "Grant authorizations to the client application.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.1\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#AuthorizationRequest\">OpenID Connect RFC</a> for more information."
  )
  public Response get(@Context UriInfo uriInfo) {
    return post(uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @ApiOperation(
      value = "Grant authorizations to the client application.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.1\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#AuthorizationRequest\">OpenID Connect RFC</a> for more information."
  )
  public Response post(MultivaluedMap<String, String> params) {
    this.params = params;

    final String client_id = getRequiredParameter(CLIENT_ID);

    ServiceProvider serviceProvider = applicationRepository.getServiceProvider(client_id);
    if (serviceProvider == null) {
      throw accessDenied("Unknown client id");
    }

    String redirect_uri = getRequiredParameter(REDIRECT_URI);
    // Validate redirect_uri
    final URI ruri;
    try {
      ruri = new URI(redirect_uri);
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
    redirectUriBuilder = new StringBuilder(redirect_uri);

    // From now on, we can redirect to the client application, for both success and error conditions
    // Prepare the redirect_uri to end with a query-string so we can just append with '&' separators
    if (ruri.getRawQuery() == null) {
      redirectUriBuilder.append('?');
    } else if (!ruri.getRawQuery().isEmpty()) {
      redirectUriBuilder.append('&');
    }

    final String state = getParameter(STATE);
    if (state != null) {
      appendQueryParam(redirectUriBuilder, STATE, state);
    }

    final String response_type = getRequiredParameter(RESPONSE_TYPE);
    // TODO: support "implicit grant"
    if (!response_type.equals("code")) {
      throw error("unsupported_response_type", "Only 'code' is supported for now.");
    }

    final String scope = getRequiredParameter(SCOPE);
    Set<String> scopeIds = Sets.newHashSet(SPACE_SPLITTER.split(scope));
    if (!scopeIds.contains("openid")) {
      throw error("invalid_scope", "You must include 'openid'");
    }

    // TODO: OpenID Connect specifics
    String userId = ((AccountPrincipal)securityContext.getUserPrincipal()).getAccountId();

    AuthorizedScopes authorizedScopes = authorizationRepository.getAuthorizedScopes(userId, client_id);
    Set<String> grantedScopeIds;
    if (authorizedScopes != null) {
      grantedScopeIds = authorizedScopes.getScopeIds();
    } else {
      grantedScopeIds = Collections.emptySet();
    }

    if (grantedScopeIds.containsAll(scopeIds)) {
      // User already granted all requested scopes, let it be a "transparent" redirect
      String nonce = getParameter(NONCE);
      AuthorizationCode authCode = tokenHandler.createAuthorizationCode(userId, scopeIds, client_id, nonce, redirect_uri);

      if (authCode == null) {
        return Response.serverError().build();
      }

      String auth_code = TokenSerializer.serialize(authCode);

      appendQueryParam(redirectUriBuilder, "code", auth_code);
      return Response.seeOther(URI.create(redirectUriBuilder.toString())).build();
    }

    Set<String> globalClaimedScopeIds = Sets.newHashSet();
    Iterable<ScopeCardinality> scopeCardinalities = serviceProvider.getScopeCardinalities();
    if (scopeCardinalities != null) {
      for (ScopeCardinality scopeCardinality : scopeCardinalities) {
        globalClaimedScopeIds.add(scopeCardinality.getScopeId());
      }
    }
    globalClaimedScopeIds.addAll(scopeIds);
    // TODO: Manage automatically granted scopes
    // Note: the openid scope is later re-added with a hidden field in the approval form
    globalClaimedScopeIds.remove("openid");

    Iterable<Scope> globalClaimedScopes;
    try {
      globalClaimedScopes = authorizationRepository.getScopes(globalClaimedScopeIds);
    } catch (IllegalArgumentException e) {
      throw error("invalid_scope", e.getMessage());
    }

    // Some scopes need explicit approval, generate approval form
    List<Scope> requiredScopes = Lists.newArrayList();
    List<Scope> optionalScopes = Lists.newArrayList();
    List<Scope> alreadyGrantedScopes = Lists.newArrayList();
    for (Scope claimedScope : globalClaimedScopes) {
      if (grantedScopeIds.contains(claimedScope.getId())) {
        alreadyGrantedScopes.add(claimedScope);
      } else if (scopeIds.contains(claimedScope.getId())) {
        requiredScopes.add(claimedScope);
      } else {
        optionalScopes.add(claimedScope);
      }
    }

    // TODO: Get the application in order to have more information

    // TODO: Make a URI Service in order to move the URI logic outside of the JAX-RS resource
    // redirectUriBuilder is now used for creating the cancel Uri for the authorization step with the user
    appendQueryParam(redirectUriBuilder, "error", "access_denied");

    // TODO: Improve security by adding a token created by encrypting scopes with a secret
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new View("oasis/web/authz/Approve.get.html",
            ImmutableMap.of(
                "urls", ImmutableMap.of(
                "continue", uriInfo.getRequestUri(),
                "cancel", redirectUriBuilder.toString(),
                "formAction", UriBuilder.fromResource(AuthorizationEndpoint.class).path(APPROVE_PATH).build()
            ),
                "scopes", ImmutableMap.of(
                "requiredScopes", requiredScopes,
                "optionalScopes", optionalScopes,
                "alreadyGrantedScopes", alreadyGrantedScopes
            ),
                "app", ImmutableMap.of(
                "id", client_id,
                "name", serviceProvider.getName()
            )
            )
        ))
        .build();
  }

  @POST
  @Path(APPROVE_PATH)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response postScopes(
      @FormParam("scope") Set<String> scopeIds,
      @FormParam("continue") URI continueUrl,
      @FormParam("client_id") String client_id) {
    // TODO: CSRF Validation
    String userId = ((AccountPrincipal)securityContext.getUserPrincipal()).getAccountId();

    authorizationRepository.authorize(userId, client_id, scopeIds);

    return Response.seeOther(continueUrl).build();
  }

  private void appendQueryParam(StringBuilder stringBuilder, String paramName, String paramValue) {
    Escaper escaper = UrlEscapers.urlFormParameterEscaper();
    assert escaper.escape(paramName).equals(paramName) : "paramName needs escaping!";
    stringBuilder.append(paramName).append('=').append(escaper.escape(paramValue)).append('&');
  }

  private WebApplicationException invalidParam(String paramName) {
    return invalidRequest("Invalid parameter value: " + paramName);
  }

  private WebApplicationException invalidRequest(String message) {
    return error("invalid_request", message);
  }

  private WebApplicationException accessDenied(String message) {
    return error("access_denied", message);
  }

  private WebApplicationException error(String error, @Nullable String description) {
    if (redirectUriBuilder == null) {
      if (description != null) {
        error += ": " + description;
      }
      return new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN)
          .entity(error)
          .build());
    }
    appendQueryParam(redirectUriBuilder, "error", error);
    if (description != null) {
      appendQueryParam(redirectUriBuilder, "error_description", description);
    }
    return new RedirectionException(Response.seeOther(URI.create(redirectUriBuilder.toString())).build());
  }

  /**
   * Returns a parameter value out of the parameters map.
   * <p>
   * Trims the value and normalizes the empty value to {@code null}.
   * <p>
   * If the parameter is included more than once, a {@link WebApplicationException} is thrown that will either display
   * the error to the user or redirect to the client application, depending on whether the {@link #redirectUriBuilder} field
   * is {@code null} or not.
   *
   * @param     paramName the parameter name
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
   * whether the {@link #redirectUriBuilder} field is {@code null} or not.
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
