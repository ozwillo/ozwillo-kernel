package oasis.web.authz;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Clock;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstance.NeededScope;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.openidconnect.RedirectUri;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.Authenticated;
import oasis.web.authn.User;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.view.SoyView;
import oasis.web.view.soy.AuthorizeSoyInfo;
import oasis.web.view.soy.AuthorizeSoyInfo.AuthorizeSoyTemplateInfo;

@Path("/a/auth")
@User
@Produces(MediaType.TEXT_HTML)
@Api(value = "/a/auth", description = "Authorization Endpoint.")
public class AuthorizationEndpoint {
  private static final String APPROVE_PATH = "/approve";

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();

  private class Prompt {
    boolean interactive = true;
    boolean login;
    boolean consent;
    boolean selectAccount;

    @Override
    public String toString() {
      if (!interactive) {
        return "none";
      }
      return Joiner.on(' ').skipNulls().join(Arrays.asList(
          login ? "login" : null,
          consent ? "consent" : null,
          selectAccount ? "select_account" : null
      ));
    }
  }

  @Context SecurityContext securityContext;

  @Inject OpenIdConnectModule.Settings settings;
  @Inject AuthorizationRepository authorizationRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject ScopeRepository scopeRepository;
  @Inject TokenHandler tokenHandler;
  @Inject JsonFactory jsonFactory;
  @Inject Clock clock;

  private MultivaluedMap<String, String> params;
  private RedirectUri redirectUri;

  @GET
  @ApiOperation(
      value = "Grant authorizations to the client application.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.1\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#AuthorizationRequest\">OpenID Connect RFC</a> for more information."
  )
  public Response get(@Context UriInfo uriInfo) {
    return post(uriInfo, uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @ApiOperation(
      value = "Grant authorizations to the client application.",
      notes = "See the <a href=\"http://tools.ietf.org/html/rfc6749#section-3.1\">OAuth 2.0 RFC</a> and " +
          "<a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#AuthorizationRequest\">OpenID Connect RFC</a> for more information."
  )
  public Response post(@Context UriInfo uriInfo, MultivaluedMap<String, String> params) {
    this.params = params;

    final String client_id = getRequiredParameter("client_id");
    final AppInstance appInstance = getAppInstance(client_id);

    final String redirect_uri = getRequiredParameter("redirect_uri");
    if (!isRedirectUriValid(redirect_uri, appInstance.getId())) {
      throw invalidParam("redirect_uri");
    }
    // From now on, we can redirect to the client application, for both success and error conditions

    redirectUri = new RedirectUri(redirect_uri);

    // we should send the state back to the client if provided, so it's the first thing to get after validating the
    // client_id and redirect_uri (i.e. after verifying that it's OK to redirect to the client)
    // In case of error retrieving the state (i.e. multi-valued), we'll thus redirect to the client
    // without a state, which is OK (and the expected behavior)
    final String state = getParameter("state");
    redirectUri.setState(state);

    final String response_type = getRequiredParameter("response_type");
    final String response_mode = getParameter("response_mode");
    validateResponseTypeAndMode(response_type, response_mode);

    final String scope = getRequiredParameter("scope");
    Set<String> scopeIds = Sets.newHashSet(SPACE_SPLITTER.split(scope));
    validateScopeIds(scopeIds);

    // TODO: OpenID Connect specifics

    if (params.containsKey("request")) {
      throw error("request_not_supported", null);
    }
    if (params.containsKey("request_uri")) {
      throw error("request_uri_not_supported", null);
    }

    final Prompt prompt = parsePrompt(getParameter("prompt"));
    if (securityContext.getUserPrincipal() == null || prompt.login) {
      if (!prompt.interactive) {
        throw error("login_required", null);
      }
      return redirectToLogin(uriInfo, prompt);
    }

    // Ignore offline_access without prompt=consent
    if (scopeIds.contains("offline_access") && !prompt.consent) {
      scopeIds.remove("offline_access");
    }

    final SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();

    final String id_token_hint = getParameter("id_token_hint");
    validateIdTokenHint(uriInfo, sidToken, id_token_hint);

    final String max_age = getParameter("max_age");
    if (max_age != null) {
      final long maxAge;
      try {
        maxAge = Long.parseLong(max_age);
      } catch (NumberFormatException nfe) {
        throw invalidParam("max_age");
      }
      if (sidToken.getAuthenticationTime().plus(TimeUnit.SECONDS.toMillis(maxAge)).isBefore(clock.currentTimeMillis())) {
        return redirectToLogin(uriInfo, prompt);
      }
    }

    final String nonce = getParameter("nonce");

    Set<String> authorizedScopeIds = getAuthorizedScopeIds(appInstance.getId(), sidToken.getAccountId());
    if (authorizedScopeIds.containsAll(scopeIds) && !prompt.consent) {
      // User already authorized all claimed scopes, let it be a "transparent" redirect
      return generateAuthorizationCodeAndRedirect(sidToken, scopeIds, appInstance.getId(), nonce, redirect_uri);
    }

    if (!prompt.interactive) {
      throw error("consent_required", null);
    }
    return promptUser(appInstance, scopeIds, authorizedScopeIds, redirect_uri, state, nonce);
  }

  @POST
  @Authenticated
  @Path(APPROVE_PATH)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response postScopes(
      @FormParam("scope") Set<String> scopeIds,
      @FormParam("selected_scope") Set<String> selectedScopeIds,
      @FormParam("client_id") String client_id,
      @FormParam("redirect_uri") String redirect_uri,
      @Nullable @FormParam("state") String state,
      @Nullable @FormParam("nonce") String nonce
  ) {
    // TODO: check CSRF / XSS (check data hasn't been tampered since generation of the form, so we can skip some validations we had already done)

    redirectUri = new RedirectUri(redirect_uri).setState(state);

    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();

    authorizationRepository.authorize(sidToken.getAccountId(), client_id, selectedScopeIds);

    return generateAuthorizationCodeAndRedirect(sidToken, scopeIds, client_id, nonce, redirect_uri);
  }

  private Response redirectToLogin(UriInfo uriInfo, Prompt prompt) {
    // Prepare cancel URL
    redirectUri.setError("login_required", null);
    // Redirect back to here, except without prompt=login
    prompt.login = false;
    String promptValue = prompt.toString();
    UriBuilder continueUrl = uriInfo.getRequestUriBuilder();
    if (Strings.isNullOrEmpty(promptValue)) {
      // remove the prompt parameter entirely
      continueUrl.replaceQueryParam("prompt");
    } else {
      continueUrl.replaceQueryParam("prompt", promptValue);
    }
    return UserAuthenticationFilter.loginResponse(continueUrl.build(), redirectUri.toString(), securityContext);
  }

  private Response generateAuthorizationCodeAndRedirect(SidToken sidToken, Set<String> scopeIds, String client_id,
      @Nullable String nonce, String redirect_uri) {
    String pass = tokenHandler.generateRandom();
    AuthorizationCode authCode = tokenHandler.createAuthorizationCode(sidToken, scopeIds, client_id, nonce, redirect_uri, pass);

    String auth_code = TokenSerializer.serialize(authCode, pass);

    if (auth_code == null) {
      return Response.serverError().build();
    }

    redirectUri.setCode(auth_code);
    return Response.seeOther(URI.create(redirectUri.toString())).build();
  }

  private Response promptUser(AppInstance serviceProvider, Set<String> requiredScopeIds, Set<String> authorizedScopeIds,
      String redirect_uri, @Nullable String state, @Nullable String nonce) {
    Set<String> globalClaimedScopeIds = Sets.newHashSet();
    Set<NeededScope> neededScopes = serviceProvider.getNeeded_scopes();
    if (neededScopes != null) {
      // TODO: display needed scope motivation
      for (NeededScope neededScope : neededScopes) {
        globalClaimedScopeIds.add(neededScope.getScope_id());
      }
    }
    globalClaimedScopeIds.addAll(requiredScopeIds);
    // TODO: Manage automatically authorized scopes

    Iterable<Scope> globalClaimedScopes;
    try {
      globalClaimedScopes = scopeRepository.getScopes(globalClaimedScopeIds);
    } catch (IllegalArgumentException e) {
      throw error("invalid_scope", e.getMessage());
    }

    // Some scopes need explicit approval, generate approval form
    SoyListData missingScopes = new SoyListData();
    SoyListData optionalScopes = new SoyListData();
    SoyListData alreadyAuthorizedScopes = new SoyListData();
    for (Scope claimedScope : globalClaimedScopes) {
      String scopeId = claimedScope.getId();
      SoyMapData scope = new SoyMapData(
          AuthorizeSoyInfo.Param.ID, scopeId,
          // TODO: I18N
          AuthorizeSoyInfo.Param.TITLE, claimedScope.getName().get(Locale.ROOT),
          AuthorizeSoyInfo.Param.DESCRIPTION, claimedScope.getDescription().get(Locale.ROOT)
      );
      if (authorizedScopeIds.contains(scopeId)) {
        alreadyAuthorizedScopes.add(scope);
      } else if (requiredScopeIds.contains(scopeId)) {
        missingScopes.add(scope);
      } else {
        optionalScopes.add(scope);
      }
    }

    // TODO: Get the application in order to have more information

    // redirectUri is now used for creating the cancel Uri for the authorization step with the user
    redirectUri.setError("access_denied", null);

    // TODO: Improve security by adding a token created by encrypting scopes with a secret
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyView(AuthorizeSoyInfo.AUTHORIZE,
            new SoyMapData(
                AuthorizeSoyTemplateInfo.APP_ID, serviceProvider.getId(),
                // TODO: I18N
                AuthorizeSoyTemplateInfo.APP_NAME, serviceProvider.getName().get(Locale.ROOT),
                AuthorizeSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(AuthorizationEndpoint.class).path(APPROVE_PATH).build().toString(),
                AuthorizeSoyTemplateInfo.CANCEL_URL, redirectUri.toString(),
                AuthorizeSoyTemplateInfo.REQUIRED_SCOPES, new SoyListData(requiredScopeIds),
                AuthorizeSoyTemplateInfo.MISSING_SCOPES, missingScopes,
                AuthorizeSoyTemplateInfo.OPTIONAL_SCOPES, optionalScopes,
                AuthorizeSoyTemplateInfo.ALREADY_AUTHORIZED_SCOPES, alreadyAuthorizedScopes,
                AuthorizeSoyTemplateInfo.REDIRECT_URI, redirect_uri,
                AuthorizeSoyTemplateInfo.STATE, state,
                AuthorizeSoyTemplateInfo.NONCE, nonce
            )
        ))
        .build();
  }

  private AppInstance getAppInstance(String client_id) {
    AppInstance appInstance = appInstanceRepository.getAppInstance(client_id);
    if (appInstance == null) {
      throw accessDenied("Unknown client id");
    }
    return appInstance;
  }

  private boolean isRedirectUriValid(String redirect_uri, String instanceId) {
    return (settings.disableRedirectUriValidation || serviceRepository.getServiceByRedirectUri(instanceId, redirect_uri) != null)
        // Note: validate the URI even if it's in the whitelist, just in case. You can never be too careful.
        && RedirectUri.isValid(redirect_uri);
  }

  private void validateResponseTypeAndMode(String response_type, @Nullable String responseMode) {
    if (!response_type.equals("code")) {
      throw error("unsupported_response_type", "Only 'code' is supported for now.");
    }
    if (responseMode == null) {
      return;
    }
    if (!responseMode.equals("query")) {
      throw invalidParam("response_mode");
    }
  }

  private Set<String> validateScopeIds(Set<String> scopeIds) {
    if (!scopeIds.contains("openid")) {
      throw error("invalid_scope", "You must include 'openid'");
    }
    return scopeIds;
  }

  private Prompt parsePrompt(String prompt) {
    Prompt ret = new Prompt();
    if (prompt == null) {
      return ret;
    }
    Set<String> promptValues = Sets.newHashSet(SPACE_SPLITTER.split(prompt));
    ret.interactive = !promptValues.remove("none");
    if (!ret.interactive && !promptValues.isEmpty()) {
      // none is not alone
      throw invalidParam("prompt");
    }
    ret.login = promptValues.remove("login");
    ret.consent = promptValues.remove("consent");
    ret.selectAccount = promptValues.remove("select_account");
    if (!promptValues.isEmpty()) {
      // Unknown prompt value(s)
      throw invalidParam("prompt");
    }
    return ret;
  }

  private void validateIdTokenHint(UriInfo uriInfo, SidToken sidToken, String id_token_hint) {
    if (id_token_hint != null) {
      final IdToken idTokenHint;
      try {
        idTokenHint = IdToken.parse(jsonFactory, id_token_hint);
        if (!idTokenHint.verifySignature(settings.keyPair.getPublic()) ||
            !idTokenHint.verifyIssuer(Resteasy1099.getBaseUri(uriInfo).toString())) {
          throw invalidParam("id_token_hint");
        }
      } catch (WebApplicationException wae) {
        throw wae;
      } catch (Throwable t) {
        throw invalidParam("id_token_hint");
      }
      if (!sidToken.getAccountId().equals(idTokenHint.getPayload().getSubject())) {
        // See https://bitbucket.org/openid/connect/issue/878/messages-2111-define-negative-response-for
        // for a discussion of the error to use here.
        throw error("login_required", null);
      }
    }
  }

  private Set<String> getAuthorizedScopeIds(String client_id, String userId) {
    AuthorizedScopes authorizedScopes = authorizationRepository.getAuthorizedScopes(userId, client_id);
    if (authorizedScopes == null) {
      return Collections.emptySet();
    }
    return authorizedScopes.getScope_ids();
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
    if (redirectUri == null) {
      if (description != null) {
        error += ": " + description;
      }
      return new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN)
          .entity(error)
          .build());
    }
    redirectUri.setError(error, description);
    return new RedirectionException(Response.seeOther(URI.create(redirectUri.toString())).build());
  }

  /**
   * Returns a parameter value out of the parameters map.
   * <p>
   * Trims the value and normalizes the empty value to {@code null}.
   * <p>
   * If the parameter is included more than once, a {@link WebApplicationException} is thrown that will either display
   * the error to the user or redirect to the client application, depending on whether the {@link #redirectUri} field
   * is {@code null} or not.
   *
   * @param     paramName the parameter name
   * @return the parameter (unique) value or {@code null} if absent or empty
   * @throws javax.ws.rs.WebApplicationException if the parameter is included more than once.
   */
  @Nullable
  private String getParameter(String paramName) {
    if (params == null) { // Workaround for https://issues.jboss.org/browse/RESTEASY-1004
      return null;
    }
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
   * whether the {@link #redirectUri} field is {@code null} or not.
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
