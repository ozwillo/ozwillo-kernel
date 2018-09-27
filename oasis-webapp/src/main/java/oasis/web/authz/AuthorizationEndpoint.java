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

import static java.util.function.Predicate.isEqual;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlBuilder;
import com.google.common.html.types.SafeHtmls;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.RedirectUri;
import oasis.auth.ScopesAndClaims;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Address;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstance.NeededScope;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.AuthorizationContextClasses;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.authz.AuthorizedScopes;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.services.cookies.CookieFactory;
import oasis.services.security.OriginHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.AuthorizeSoyInfo;
import oasis.soy.templates.AuthorizeSoyInfo.AskForClientCertificateSoyTemplateInfo;
import oasis.soy.templates.AuthorizeSoyInfo.AuthorizeSoyTemplateInfo;
import oasis.soy.templates.UserCertificatesSoyInfo;
import oasis.urls.BaseUrls;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.ClientCertificateHelper;
import oasis.web.authn.ClientCertificateHelper.ClientCertificateData;
import oasis.web.authn.SessionManagementHelper;
import oasis.web.authn.User;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authn.UserCertificatesPage;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.openidconnect.IdTokenHintParser;
import oasis.web.security.StrictReferer;

@Path("/a/auth")
@User
@Produces(MediaType.TEXT_HTML)
public class AuthorizationEndpoint {
  private static final String APPROVE_PATH = "/approve";

  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();
  private static final CharMatcher CODE_CHALLENGE_MATCHER = CharMatcher
      .inRange('A', 'Z').or(CharMatcher.inRange('a', 'z')) // ALPHA
      .or(CharMatcher.inRange('0', '9')) // DIGIT
      .or(CharMatcher.anyOf("-._~"))
      .precomputed();

  private static class Prompt {
    boolean interactive = true;
    boolean login;
    boolean consent;
    boolean selectAccount;

    @Override
    public String toString() {
      if (!interactive) {
        return "none";
      }
      StringJoiner joiner = new StringJoiner(" ");
      if (login) {
        joiner.add("login");
      }
      if (consent) {
        joiner.add("consent");
      }
      if (selectAccount) {
        joiner.add("select_account");
      }
      return joiner.toString();
    }
  }

  private static final ImmutableMap<String, Function<UserAccount, Object>> CLAIM_PROVIDERS = ImmutableMap.<String, Function<UserAccount, Object>>builder()
      .put("name", UserAccount::getName)
      .put("family_name", UserAccount::getFamily_name)
      .put("given_name", UserAccount::getGiven_name)
      .put("middle_name", UserAccount::getMiddle_name)
      .put("nickname", UserAccount::getNickname)
      .put("gender", UserAccount::getGender)
      .put("birthdate", userAccount -> {
        if (userAccount.getBirthdate() == null) {
          return null;
        }
        DateFormat dt = DateFormat.getDateInstance(DateFormat.SHORT, userAccount.getLocale());
        return dt.format(Date.from(userAccount.getBirthdate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
      })
      .put("locale", userAccount -> {
        if (userAccount.getLocale() == null){
          return null;
        }
        return userAccount.getLocale().getDisplayName(userAccount.getLocale());
      })
      .put("email", UserAccount::getEmail_address)
      .put("address", userAccount -> {
        Address address = userAccount.getAddress();
        if (address == null || address.isEmpty()){
          return null;
        }
        List<SafeHtml> listSafeHtml = Stream.of(
            address.getStreet_address(),
            Stream.of(
                address.getPostal_code(),
                address.getLocality()
            ).filter(Objects::nonNull).collect(Collectors.joining(" ")),
            address.getRegion(),
            address.getCountry()
        ).filter(Objects::nonNull)
            .map(s -> new SafeHtmlBuilder("p").appendContent(SafeHtmls.htmlEscape(s)).build())
            .collect(Collectors.toList());

        return new SafeHtmlBuilder("div").appendContent(listSafeHtml).build();
      })
      .put("phone_number", UserAccount::getPhone_number)
      .build();

  @Context SecurityContext securityContext;
  @Context Request request;
  @Context HttpHeaders httpHeaders;

  @Inject AuthModule.Settings settings;
  @Inject AuthorizationRepository authorizationRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AccountRepository accountRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject ScopeRepository scopeRepository;
  @Inject TokenHandler tokenHandler;
  @Inject LocaleHelper localeHelper;
  @Inject Clock clock;
  @Inject BaseUrls baseUrls;
  @Inject SessionManagementHelper sessionManagementHelper;
  @Inject ClientCertificateHelper helper;
  @Inject ClientCertificateRepository clientCertificateRepository;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  private MultivaluedMap<String, String> params;
  private String client_id;
  private String redirect_uri;
  private RedirectUri redirectUri;

  private final Supplier<UserAccount> userAccountSupplier = Suppliers.memoize(() ->
      accountRepository.getUserAccountById(((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId()));

  @GET
  public Response get(@Context UriInfo uriInfo) {
    return post(uriInfo, uriInfo.getQueryParameters());
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response post(@Context UriInfo uriInfo, MultivaluedMap<String, String> params) {
    this.params = params;

    client_id = getRequiredParameter("client_id");
    final AppInstance appInstance = getAppInstance(client_id);

    redirect_uri = getRequiredParameter("redirect_uri");
    @Nullable final Service service = serviceRepository.getServiceByRedirectUri(appInstance.getId(), redirect_uri);
    if (!isRedirectUriValid(appInstance, service, redirect_uri)) {
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
    if (scopeIds.contains(Scopes.OFFLINE_ACCESS) && !prompt.consent) {
      scopeIds.remove(Scopes.OFFLINE_ACCESS);
    }

    final SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();

    final String id_token_hint = getParameter("id_token_hint");
    validateIdTokenHint(uriInfo, sidToken, id_token_hint);

    final boolean isPortal = ClientIds.PORTAL.equals(client_id);

    // Check ACL if the service is "private" (unless it's the Portal)
    if ((service != null && service.isAccessRestricted()) && !isPortal) {
      boolean isAppUser = accessControlRepository.getAccessControlEntry(appInstance.getId(), sidToken.getAccountId()) != null;
      boolean isAppAdmin = appAdminHelper.isAdmin(sidToken.getAccountId(), appInstance);
      if (!isAppUser && !isAppAdmin) {
        throw accessDenied("Current user is neither an app_admin or app_user for the service");
      }
    }

    final String max_age = getParameter("max_age");
    if (max_age != null) {
      final long maxAge;
      try {
        maxAge = Long.parseLong(max_age);
      } catch (NumberFormatException nfe) {
        throw invalidParam("max_age");
      }
      if (sidToken.getAuthenticationTime().plusSeconds(maxAge).isBefore(clock.instant())) {
        return redirectToLogin(uriInfo, prompt);
      }
    }

    final String nonce = getParameter("nonce");
    final String code_challenge = getParameter("code_challenge");
    if (code_challenge != null) {
      final String code_challenge_method = getParameter("code_challenge_method");
      if (!"S256".equals(code_challenge_method)) {
        throw invalidParam("code_challenge_method");
      }
      if (!isCodeChallengeValid(code_challenge)) {
        throw invalidParam("code_challenge");
      }
    }
    final String acr_values = getParameter("acr_values");
    boolean askForClientCertificate = false;
    if (settings.enableClientCertificates && acr_values != null) {
      // XXX: switch acr values (eIDAS, STORK-QAA, FICAM, etc.) depending on client_id and/or acr_values of the request
      // E.g. select a "scheme" based on input values (store it in auth code, use it for acr claim in TokenEndpoint)
      if (Streams.stream(SPACE_SPLITTER.split(acr_values)).anyMatch(isEqual(AuthorizationContextClasses.EIDAS_SUBSTANTIAL)) &&
          !sidToken.isUsingClientCertificate()) {
        askForClientCertificate = true;
      }
    }

    final String claims = getParameter("claims");
    ImmutableMap<String, Boolean> parsedClaims = parseClaims(claims);
    if (parsedClaims == null) {
      throw invalidParam("claims");
    }
    final ScopesAndClaims scopesAndClaims = ScopesAndClaims.of(scopeIds, parsedClaims.keySet());
    // reintegrate claims mapped from scopes as voluntary claims
    parsedClaims = mergeClaims(parsedClaims, scopesAndClaims.getClaimNames());

    ScopesAndClaims authorizedScopesAndClaims = getAuthorizedScopesAndClaims(appInstance.getId(), sidToken.getAccountId());
    if (isPortal && !authorizedScopesAndClaims.containsAll(scopesAndClaims)) {
      // Automatically grant all the Portal's needed_scopes to any user, and thus skip the prompt
      ImmutableSet<String> portalScopeIds = appInstance.getNeeded_scopes().stream()
          .map(NeededScope::getScope_id)
          .collect(ImmutableSet.toImmutableSet());
      ScopesAndClaims portalScopesAndClaims = ScopesAndClaims.of(portalScopeIds, ImmutableSet.of());
      if (!authorizedScopesAndClaims.containsAll(portalScopesAndClaims)) {
        authorizationRepository.authorize(sidToken.getAccountId(), appInstance.getId(), portalScopeIds, portalScopesAndClaims.getClaimNames());
        authorizedScopesAndClaims = authorizedScopesAndClaims.union(portalScopesAndClaims);
      }
    }
    if (authorizedScopesAndClaims.containsAll(scopesAndClaims) && !prompt.consent &&
        (!parsedClaims.containsValue(true) || profileFulfillsEssentialClaims(parsedClaims))) {
      // User already authorized all claimed scopes (and no essential claim is missing), let it be a "transparent" redirect
      if (askForClientCertificate) {
        // that is, unless we need to ask the user for a client certificate
        return askForClientCertificate(uriInfo, sidToken.getAccountId(), appInstance, scopeIds, parsedClaims.keySet(), redirect_uri, state, nonce, code_challenge);
      }
      return generateAuthorizationCodeAndRedirect(sidToken, scopeIds, scopesAndClaims.getClaimNames(), appInstance.getId(), nonce, redirect_uri, code_challenge);
    }

    if (!prompt.interactive) {
      throw error("consent_required", null);
    }
    return promptUser(uriInfo, sidToken.getAccountId(), appInstance, scopeIds, parsedClaims, authorizedScopesAndClaims, redirect_uri, state, nonce, code_challenge, askForClientCertificate);
  }

  @POST
  @Authenticated
  @StrictReferer
  @Path(APPROVE_PATH)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response postScopes(
      @FormParam("scope") Set<String> scopeIds,
      @FormParam("selected_scope") Set<String> selectedScopeIds,
      @FormParam("claim") Set<String> claimNames,
      @FormParam("client_id") String client_id,
      @FormParam("redirect_uri") String redirect_uri,
      @Nullable @FormParam("state") String state,
      @Nullable @FormParam("nonce") String nonce,
      @Nullable @FormParam("code_challenge") String code_challenge,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    // TODO: check XSS (check data hasn't been tampered since generation of the form, so we can skip some validations we had already done)

    redirectUri = new RedirectUri(redirect_uri).setState(state);

    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();

    authorizationRepository.authorize(sidToken.getAccountId(), client_id, selectedScopeIds, claimNames);

    return generateAuthorizationCodeAndRedirect(sidToken, scopeIds, claimNames, client_id, nonce, redirect_uri, code_challenge);
  }

  private Response redirectToLogin(UriInfo uriInfo, Prompt prompt) {
    String ui_locales = getParameter("ui_locales");
    ULocale locale = (ui_locales == null)
        ? localeHelper.selectLocale(request)
        : localeHelper.selectLocale(SPACE_SPLITTER.split(ui_locales), request);
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
    return UserAuthenticationFilter.loginResponse(continueUrl.build(), locale,
        redirectUri.toString(), BrandHelper.getBrandIdFromUri(uriInfo)); // TODO : get brandId from AppInstance
  }

  private Response generateAuthorizationCodeAndRedirect(SidToken sidToken, Set<String> scopeIds, Set<String> claims, String client_id,
      @Nullable String nonce, String redirect_uri, @Nullable String code_challenge) {
    String pass = tokenHandler.generateRandom();
    AuthorizationCode authCode = tokenHandler.createAuthorizationCode(sidToken, scopeIds, claims, client_id, nonce, redirect_uri, code_challenge, pass);

    String auth_code = TokenSerializer.serialize(authCode, pass);
    if (auth_code == null) {
      return Response.serverError().build();
    }
    redirectUri.setCode(auth_code);

    final Response.ResponseBuilder rb = Response.status(Response.Status.SEE_OTHER);
    setSessionState(rb, client_id, redirect_uri);

    return rb.location(URI.create(redirectUri.toString())).build();
  }

  private void setSessionState(Response.ResponseBuilder rb, String client_id, String redirect_uri) {
    Cookie browserStateCookie = httpHeaders.getCookies().get(CookieFactory.getCookieName(SessionManagementHelper.COOKIE_NAME, securityContext.isSecure()));
    String browserState = browserStateCookie == null ? null : browserStateCookie.getValue();
    if (Strings.isNullOrEmpty(browserState)) {
      browserState = sessionManagementHelper.generateBrowserState();
      rb.cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), browserState));
    }
    redirectUri.setSessionState(sessionManagementHelper.computeSessionState(client_id, redirect_uri, browserState));
  }

  private Response askForClientCertificate(UriInfo uriInfo, String accountId, AppInstance serviceProvider, Set<String> requiredScopeIds, Set<String> claims,
      String redirect_uri, @Nullable String state, @Nullable String nonce, @Nullable String code_challenge) {
    UserAccount account = userAccountSupplier.get();

    // redirectUri is now used for creating the cancel Uri for the authorization step with the user
    redirectUri.setError("access_denied", null);

    Response.ResponseBuilder rb = Response.ok();
    setSessionState(rb, serviceProvider.getId(), redirect_uri);

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(10)
        .put(AskForClientCertificateSoyTemplateInfo.APP_ID, serviceProvider.getId())
        .put(AskForClientCertificateSoyTemplateInfo.APP_NAME, serviceProvider.getName().get(account.getLocale()))
        .put(AskForClientCertificateSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(AuthorizationEndpoint.class).path(APPROVE_PATH).build().toString())
        .put(AskForClientCertificateSoyTemplateInfo.CANCEL_URL, redirectUri.toString())
        .put(AskForClientCertificateSoyTemplateInfo.SCOPES, requiredScopeIds)
        .put(AskForClientCertificateSoyTemplateInfo.CLAIMS, claims)
        .put(AskForClientCertificateSoyTemplateInfo.REDIRECT_URI, redirect_uri)
        .put(AskForClientCertificateSoyTemplateInfo.ASK_FOR_CLIENT_CERTIFICATE, getAskForClientCertificateData(uriInfo, accountId));
    if (state != null) {
      data.put(AskForClientCertificateSoyTemplateInfo.STATE, state);
    }
    if (nonce != null) {
      data.put(AskForClientCertificateSoyTemplateInfo.NONCE, nonce);
    }
    if (code_challenge != null) {
      data.put(AskForClientCertificateSoyTemplateInfo.CODE_CHALLENGE, code_challenge);
    }

    // TODO: Improve security by adding a token created by encrypting scopes with a secret
    return rb
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(AuthorizeSoyInfo.ASK_FOR_CLIENT_CERTIFICATE, account.getLocale(), data.build(),
            brandRepository.getBrandInfo(serviceProvider.getBrandId())))
        .build();
  }

  private Response promptUser(UriInfo uriInfo, String accountId, AppInstance serviceProvider, Set<String> requiredScopeIds, Map<String, Boolean> parsedClaims, ScopesAndClaims authorizedScopesAndClaims,
      String redirect_uri, @Nullable String state, @Nullable String nonce, @Nullable String code_challenge, boolean askForClientCertificate) {
    Set<String> globalClaimedScopeIds = new HashSet<>();
    // XXX: add needed claims?
    Set<NeededScope> neededScopes = serviceProvider.getNeeded_scopes();
    if (neededScopes != null) {
      // TODO: display needed scope motivation
      for (NeededScope neededScope : neededScopes) {
        globalClaimedScopeIds.add(neededScope.getScope_id());
      }
    }
    globalClaimedScopeIds.addAll(requiredScopeIds);
    // TODO: Manage automatically authorized scopes
    // Ignore scopes that we handle as claims
    globalClaimedScopeIds.removeAll(Scopes.SCOPES_TO_CLAIMS.keySet());

    Iterable<Scope> globalClaimedScopes;
    try {
      globalClaimedScopes = scopeRepository.getScopes(globalClaimedScopeIds);
    } catch (IllegalArgumentException e) {
      throw error("invalid_scope", e.getMessage());
    }

    UserAccount account = userAccountSupplier.get();

    // Some scopes need explicit approval, generate approval form
    ImmutableList.Builder<ImmutableMap<String, String>> missingScopes = ImmutableList.builder();
    ImmutableList.Builder<ImmutableMap<String, String>> optionalScopes = ImmutableList.builder();
    ImmutableList.Builder<ImmutableMap<String, String>> alreadyAuthorizedScopes = ImmutableList.builder();
    for (Scope claimedScope : globalClaimedScopes) {
      String scopeId = claimedScope.getId();
      ImmutableMap.Builder<String, String> scope = ImmutableMap.<String, String>builderWithExpectedSize(3)
          .put(AuthorizeSoyInfo.Param.ID, scopeId);
      String title = claimedScope.getName().get(account.getLocale());
      if (title != null) {
          scope.put(AuthorizeSoyInfo.Param.TITLE, title);
      }
      String description = claimedScope.getDescription().get(account.getLocale());
      if (description != null) {
        scope.put(AuthorizeSoyInfo.Param.DESCRIPTION, description);
      }
      if (authorizedScopesAndClaims.getScopeIds().contains(scopeId)) {
        alreadyAuthorizedScopes.add(scope.build());
      } else if (requiredScopeIds.contains(scopeId)) {
        missingScopes.add(scope.build());
      } else {
        optionalScopes.add(scope.build());
      }
    }

    ImmutableMap.Builder<String, ImmutableMap<String, Object>> claims = ImmutableMap.builder();
    boolean allClaimsAlreadyAuthorized = true;
    boolean essentialClaimMissing = false;

    for (Map.Entry<String, Boolean> parsedClaim : parsedClaims.entrySet()){
      String claimName = parsedClaim.getKey();
      Boolean essential = parsedClaim.getValue();

      Function<UserAccount, Object> claimProvider = CLAIM_PROVIDERS.get(claimName);
      Object value = claimProvider != null ? claimProvider.apply(account) : null;
      if (essential && claimProvider != null && value == null) {
        // XXX: we only track essential claims for those that will be displayed to the user,
        // otherwise they might be blocked with missing claims that they don't know how to provide.
        // (actually, worse, a disabled submit button and no indication as to why it's disabled.)
        essentialClaimMissing = true;
      }

      ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();
      if (value != null) {
        map.put(AuthorizeSoyInfo.Param.VALUE, value);
      }
      map.put(AuthorizeSoyInfo.Param.ESSENTIAL, essential);

      boolean alreadyAuthorized = authorizedScopesAndClaims.getClaimNames().contains(claimName);
      map.put(AuthorizeSoyInfo.Param.ALREADY_AUTHORIZED, alreadyAuthorized);
      if (!alreadyAuthorized) {
        allClaimsAlreadyAuthorized = false;
      }

      claims.put(claimName, map.build());
    }

    // TODO: Get the application in order to have more information

    // redirectUri is now used for creating the cancel Uri for the authorization step with the user
    redirectUri.setError("access_denied", null);

    Response.ResponseBuilder rb = Response.ok();
    setSessionState(rb, serviceProvider.getId(), redirect_uri);

    ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builderWithExpectedSize(17)
        .put(AuthorizeSoyTemplateInfo.APP_ID, serviceProvider.getId())
        .put(AuthorizeSoyTemplateInfo.APP_NAME, serviceProvider.getName().get(account.getLocale()))
        .put(AuthorizeSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(AuthorizationEndpoint.class).path(APPROVE_PATH).build().toString())
        .put(AuthorizeSoyTemplateInfo.CANCEL_URL, redirectUri.toString())
        .put(AuthorizeSoyTemplateInfo.REQUIRED_SCOPES, ImmutableList.copyOf(requiredScopeIds))
        .put(AuthorizeSoyTemplateInfo.MISSING_SCOPES, missingScopes.build())
        .put(AuthorizeSoyTemplateInfo.OPTIONAL_SCOPES, optionalScopes.build())
        .put(AuthorizeSoyTemplateInfo.ALREADY_AUTHORIZED_SCOPES, alreadyAuthorizedScopes.build())
        .put(AuthorizeSoyTemplateInfo.REDIRECT_URI, redirect_uri)
        .put(AuthorizeSoyTemplateInfo.CLAIMS, claims.build())
        .put(AuthorizeSoyTemplateInfo.ALL_CLAIMS_ALREADY_AUTHORIZED, allClaimsAlreadyAuthorized)
        .put(AuthorizeSoyTemplateInfo.ESSENTIAL_CLAIM_MISSING, essentialClaimMissing);
    if (state != null) {
      data.put(AuthorizeSoyTemplateInfo.STATE, state);
    }
    if (nonce != null) {
      data.put(AuthorizeSoyTemplateInfo.NONCE, nonce);
    }
    if (code_challenge != null) {
      data.put(AuthorizeSoyTemplateInfo.CODE_CHALLENGE, code_challenge);
    }
    if (askForClientCertificate) {
      data.put(AuthorizeSoyTemplateInfo.ASK_FOR_CLIENT_CERTIFICATE, getAskForClientCertificateData(uriInfo, accountId));
    }
    final BrandInfo brandInfo = brandRepository.getBrandInfo(serviceProvider.getBrandId());
    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    urls.popupProfile().ifPresent(uri -> {
      String updateProfileUrl = UriBuilder.fromUri(uri)
          .queryParam("essential_claims", parsedClaims.entrySet()
              .stream()
              .filter(e -> e.getValue())
              .map(Map.Entry::getKey)
              .collect(Collectors.joining(" ")))
          .queryParam("voluntary_claims", parsedClaims.entrySet()
              .stream()
              .filter(e -> !e.getValue())
              .map(Map.Entry::getKey)
              .collect(Collectors.joining(" ")))
          .build()
          .toString();
      data.put(AuthorizeSoyTemplateInfo.UPDATE_PROFILE_URL, updateProfileUrl);
      data.put(AuthorizeSoyTemplateInfo.UPDATE_PROFILE_ORIGIN, OriginHelper.originFromUri(updateProfileUrl));
    });

    // TODO: Improve security by adding a token created by encrypting scopes with a secret
    return rb
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(AuthorizeSoyInfo.AUTHORIZE, account.getLocale(), data.build(),
            brandInfo))
        .build();
  }

  private boolean profileFulfillsEssentialClaims(ImmutableMap<String, Boolean> parsedClaims) {
    UserAccount account = userAccountSupplier.get();
    return parsedClaims.entrySet().stream()
        // Only keep the names of essential claims
        .filter(Map.Entry::getValue).map(Map.Entry::getKey)
        // XXX: only keep claims that we'll also display to the user (otherwise that'd be confusing)
        // TODO: split CLAIMS_PROVIDERS so we don't "format" the claims here (as we're only interested in null-checks)
        .map(CLAIM_PROVIDERS::get).filter(Objects::nonNull)
        // Get the claims value; all have to be present.
        .map(f -> f.apply(account)).allMatch(Objects::nonNull);
  }

  private ImmutableMap<String, Object> getAskForClientCertificateData(UriInfo uriInfo, String accountId) {
    final ImmutableMap.Builder<String, Object> askForClientCertificateData = ImmutableMap.<String, Object>builderWithExpectedSize(3)
        .put(AuthorizeSoyInfo.Param.MANAGE_CERTIFICATES_URL,
            UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "get").build().toString())
        .put(AuthorizeSoyInfo.Param.HAS_REGISTERED_CERTIFICATE,
            !Iterables.isEmpty(clientCertificateRepository.getClientCertificatesForClient(ClientType.USER, accountId)));

    ClientCertificateData clientCertificateData = helper.getClientCertificateData(httpHeaders.getRequestHeaders());
    if (clientCertificateData != null) {
      final ImmutableMap.Builder<String, Object> currentCert;
      ClientCertificate currentCertificate = clientCertificateRepository.getClientCertificate(
          clientCertificateData.getSubjectDN(), clientCertificateData.getIssuerDN());
      if (currentCertificate != null) {
        boolean linkedToOtherAccount = currentCertificate.getClient_type() != ClientType.USER ||
            !currentCertificate.getClient_id().equals(accountId);
        currentCert = ImmutableMap.<String, Object>builderWithExpectedSize(5)
            .put(UserCertificatesSoyInfo.Param.SUBJECT, currentCertificate.getSubject_dn())
            .put(UserCertificatesSoyInfo.Param.ISSUER, currentCertificate.getIssuer_dn())
            .put(UserCertificatesSoyInfo.Param.LINKED_TO_OTHER_ACCOUNT, linkedToOtherAccount);
      } else {
        currentCert = ImmutableMap.<String, Object>builderWithExpectedSize(4)
            .put(UserCertificatesSoyInfo.Param.SUBJECT, clientCertificateData.getSubjectDN())
            .put(UserCertificatesSoyInfo.Param.ISSUER, clientCertificateData.getIssuerDN());
      }
      currentCert.put(UserCertificatesSoyInfo.Param.ADD_FORM_ACTION,
          UriBuilder.fromResource(UserCertificatesPage.class).path(UserCertificatesPage.class, "addCurrent").build().toString());
      currentCert.put(UserCertificatesSoyInfo.Param.CONTINUE_URL, uriInfo.getRequestUri().toString());

      askForClientCertificateData.put(AuthorizeSoyInfo.Param.CURRENT_CERT, currentCert.build());
    }

    return askForClientCertificateData.build();
  }

  private AppInstance getAppInstance(String client_id) {
    AppInstance appInstance = appInstanceRepository.getAppInstance(client_id);
    if (appInstance == null || appInstance.getStatus() != AppInstance.InstantiationStatus.RUNNING) {
      throw accessDenied("Unknown client id");
    }
    return appInstance;
  }

  private boolean isRedirectUriValid(AppInstance appInstance, Service service, String redirect_uri) {
    return (appInstance.isRedirect_uri_validation_disabled() || service != null)
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
    if (!scopeIds.contains(Scopes.OPENID)) {
      throw error("invalid_scope", "You must include '" + Scopes.OPENID + "'");
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
    if (id_token_hint == null) {
      return;
    }
    String subject = IdTokenHintParser.parseIdTokenHintGetSubject(id_token_hint, settings.keyPair.getPublic(), getIssuer(uriInfo));
    if (subject == null) {
      throw invalidParam("id_token_hint");
    }
    if (!subject.equals(sidToken.getAccountId())) {
      // See https://bitbucket.org/openid/connect/issue/878/messages-2111-define-negative-response-for
      // for a discussion of the error to use here.
      throw error("login_required", null);
    }
  }

  private boolean isCodeChallengeValid(String code_challenge) {
    return 43 <= code_challenge.length() && code_challenge.length() <= 128
        && CODE_CHALLENGE_MATCHER.matchesAllOf(code_challenge);
  }

  @VisibleForTesting
  @Nullable
  static ImmutableMap<String, Boolean> parseClaims(@Nullable String claims) {
    if (claims == null) {
      return ImmutableMap.of();
    }

    JsonNode node;
    try {
      node = new ObjectMapper().readTree(claims);
    } catch (IOException e) {
      return null;
    }
    if (node == null || !node.isObject()) return null;
    node = node.get("userinfo");
    if (node == null) return ImmutableMap.of();
    if (!node.isObject()) return null;

    ImmutableMap.Builder<String, Boolean> result = ImmutableMap.builder();
    for (Map.Entry<String, JsonNode> entry : (Iterable<Map.Entry<String, JsonNode>>) node::fields) {
      Boolean essential;
      if (entry.getValue().isNull()) {
        essential = false;
      } else if (entry.getValue().isObject()) {
        JsonNode essentialNode = entry.getValue().get("essential");
        if (essentialNode != null && !essentialNode.isBoolean()) return null;
        essential = essentialNode != null && essentialNode.booleanValue();
      } else {
        return null;
      }

      // Ignore unknown claims / only keep supported claims
      if (Scopes.SUPPORTED_CLAIMS.contains(entry.getKey())) {
        result.put(entry.getKey(), essential);
      }
    }
    return result.build();
  }

  @VisibleForTesting
  static ImmutableMap<String, Boolean> mergeClaims(ImmutableMap<String, Boolean> parsedClaims, Set<String> claimNames) {
    return Stream.concat(parsedClaims.keySet().stream(), claimNames.stream())
        .collect(ImmutableMap.toImmutableMap(
            Function.identity(),
            claimName -> parsedClaims.getOrDefault(claimName, false),
            Boolean::logicalOr));
  }

  private String getIssuer(UriInfo uriInfo) {
    if (baseUrls.canonicalBaseUri().isPresent()) {
      return baseUrls.canonicalBaseUri().get().toString();
    }
    return uriInfo.getBaseUri().toString();
  }

  private ScopesAndClaims getAuthorizedScopesAndClaims(String client_id, String userId) {
    AuthorizedScopes authorizedScopes = authorizationRepository.getAuthorizedScopes(userId, client_id);
    if (authorizedScopes == null) {
      return ScopesAndClaims.of();
    }
    return ScopesAndClaims.of(authorizedScopes.getScope_ids(), authorizedScopes.getClaim_names());
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
    Response.ResponseBuilder rb = Response.status(Response.Status.SEE_OTHER);
    setSessionState(rb, client_id, redirect_uri);
    return new RedirectionException(rb.location(URI.create(redirectUri.toString())).build());
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
   * @throws WebApplicationException if the parameter is included more than once.
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
   * whether the {@link #redirectUri} field is {@code null} or not.
   *
   * @param paramName     the parameter name
   * @return the parameter (unique) value (cannot be {@code null}
   * @throws WebApplicationException if the parameter is absent, empty, or included more than once.
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
