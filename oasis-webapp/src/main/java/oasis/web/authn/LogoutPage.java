package oasis.web.authn;

import java.net.URI;
import java.security.PublicKey;
import java.util.Locale;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.json.JsonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Service;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.openidconnect.RedirectUri;
import oasis.services.applications.AppInstanceService;
import oasis.services.applications.ServiceService;
import oasis.services.cookies.CookieFactory;
import oasis.web.security.StrictReferer;
import oasis.web.view.View;

@User
@Path("/a/logout")
public class LogoutPage {
  private static final Logger logger = LoggerFactory.getLogger(LogoutPage.class);

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @Inject TokenRepository tokenRepository;
  @Inject OpenIdConnectModule.Settings settings;
  @Inject JsonFactory jsonFactory;
  @Inject AppInstanceService appInstanceService;
  @Inject ServiceService serviceService;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @Nullable @QueryParam("id_token_hint") String id_token_hint,
      @Nullable @QueryParam("post_logout_redirect_uri") String post_logout_redirect_uri,
      @Nullable @QueryParam("state") String state) {
    final SidToken sidToken = (securityContext.getUserPrincipal() != null)
        ? ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken()
        : null;

    final IdToken.Payload idTokenHint = parseIdTokenHint(id_token_hint, sidToken);
    post_logout_redirect_uri = Strings.emptyToNull(post_logout_redirect_uri);

    final AppInstance appInstance;
    if (idTokenHint != null) {
      final String client_id = idTokenHint.getAudienceAsList().get(0);
      appInstance = appInstanceService.getAppInstance(client_id);
    } else {
      appInstance = null;
    }

    final Service service;
    if (appInstance != null && post_logout_redirect_uri != null) {
      service = serviceService.getServiceByPostLogoutRedirectUri(appInstance.getId(), post_logout_redirect_uri);
      if (service == null && !settings.disableRedirectUriValidation) {
        // don't act as an open redirector!
        post_logout_redirect_uri = null;
      }
    } else {
      service = null;
      // don't act as an open redirector!
      post_logout_redirect_uri = null;
    }

    // Note: validate the URI even if it's in the whitelist, just in case. You can never be too careful.
    if (post_logout_redirect_uri != null && !RedirectUri.isValid(post_logout_redirect_uri)) {
      post_logout_redirect_uri = null;
    }

    if (post_logout_redirect_uri != null && !Strings.isNullOrEmpty(state)) {
      post_logout_redirect_uri = new RedirectUri(post_logout_redirect_uri)
          .setState(state)
          .toString();
    }

    if (securityContext.getUserPrincipal() == null) {
      // Not authenticated (we'll assume the user already signed out but the app didn't caught it up)
      return redirectTo(post_logout_redirect_uri != null ? URI.create(post_logout_redirect_uri) : null);
    }

    ImmutableMap.Builder<String, Object> viewModel = ImmutableMap.builder();
    viewModel.put("formAction", UriBuilder.fromResource(LogoutPage.class).build());
    if (post_logout_redirect_uri != null) {
      viewModel.put("continue", post_logout_redirect_uri);
    }
    if (appInstance != null) {
      viewModel.put("app", ImmutableMap.of(
          // TODO: I18N
          "name", appInstance.getName().get(Locale.ROOT)
      ));
    }
    // FIXME: services don't all have a service_uri for now so we need to workaround it.
    if (service != null && !Strings.isNullOrEmpty(service.getService_uri())) {
      viewModel.put("service", ImmutableMap.of(
          // TODO: I18N
          "name", service.getName().get(Locale.ROOT),
          "url", service.getService_uri()
      ));
    }
    return Response.ok(new View(LogoutPage.class, "Logout.html", viewModel.build())).build();
  }

  private Response redirectTo(@Nullable URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(settings.landingPage, uriInfo);
    }
    return Response.seeOther(continueUrl).build();
  }

  @VisibleForTesting
  @Nullable
  private IdToken.Payload parseIdTokenHint(@Nullable String idTokenHint, SidToken sidToken) {
    return parseIdTokenHint(jsonFactory, settings.keyPair.getPublic(), uriInfo.getBaseUri().toString(),
        idTokenHint, sidToken);
  }

  @VisibleForTesting
  @Nullable
  static IdToken.Payload parseIdTokenHint(JsonFactory jsonFactory, PublicKey publicKey, String issuer,
      @Nullable String idTokenHint, SidToken sidToken) {
    if (idTokenHint == null) {
      return null;
    }
    try {
      IdToken idToken = IdToken.parse(jsonFactory, idTokenHint);
      if (!idToken.verifySignature(publicKey) ||
          !idToken.verifyIssuer(issuer)) {
        return null;
      }
      IdToken.Payload payload = idToken.getPayload();
      if (payload.getAudience() == null) {
        return null;
      }
      // there must be an audience
      assert payload.getAudienceAsList() != null && !payload.getAudienceAsList().isEmpty();
      if (sidToken != null && !sidToken.getAccountId().equals(payload.getSubject())) {
        // The app asked to sign-out another session (we'll assume the user already signed out –and
        // signed in again– but the app didn't caught it up)
        return null;
      }
      return payload;
    } catch (Throwable t) {
      return null;
    }
  }

  @POST
  @Authenticated
  @StrictReferer
  public Response post(@FormParam("continue") URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(settings.landingPage, uriInfo);
    }

    final SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    boolean tokenRevoked = tokenRepository.revokeToken(sidToken.getId());
    if (!tokenRevoked) {
      logger.error("No SidToken was found when trying to revoke it.");
    }

    return Response.seeOther(continueUrl)
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure()))
        .build();
  }
}
