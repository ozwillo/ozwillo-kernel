package oasis.web.authn;

import java.net.URI;
import java.security.PublicKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
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
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.bootstrap.ClientIds;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.openidconnect.RedirectUri;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LogoutSoyInfo;
import oasis.soy.templates.LogoutSoyInfo.LogoutSoyTemplateInfo;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.security.StrictReferer;

@User
@Path("/a/logout")
public class LogoutPage {
  private static final Logger logger = LoggerFactory.getLogger(LogoutPage.class);

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @Inject TokenRepository tokenRepository;
  @Inject OpenIdConnectModule.Settings settings;
  @Inject JsonFactory jsonFactory;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;

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
      appInstance = appInstanceRepository.getAppInstance(client_id);
      if (appInstance == null) {
        logger.debug("No app instance for id_token_hint audience: {}", client_id);
      }
    } else {
      appInstance = null;
    }

    final Service service;
    if (appInstance != null && post_logout_redirect_uri != null) {
      service = serviceRepository.getServiceByPostLogoutRedirectUri(appInstance.getId(), post_logout_redirect_uri);
      if (service == null) {
        logger.debug("No service found for id_token_hint audience {} and post_logout_redirect_uri {}",
            appInstance.getId(), post_logout_redirect_uri);
      }
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
      logger.debug("Invalid post_logout_redirect_uri {}", post_logout_redirect_uri);
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

    SoyMapData viewModel = new SoyMapData();
    viewModel.put(LogoutSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(LogoutPage.class).build().toString());
    if (post_logout_redirect_uri != null) {
      viewModel.put(LogoutSoyTemplateInfo.CONTINUE, post_logout_redirect_uri);
    }
    if (appInstance != null) {
      // TODO: I18N
      viewModel.put(LogoutSoyTemplateInfo.APP_NAME, appInstance.getName().get(Locale.ROOT));
    }
    // FIXME: services don't all have a service_uri for now so we need to workaround it.
    if (service != null && !Strings.isNullOrEmpty(service.getService_uri())) {
      viewModel.put(LogoutSoyTemplateInfo.SERVICE_URL, service.getService_uri());
    }
    ArrayList<String> otherApps = new ArrayList<>();
    for (AppInstance otherAppInstance : appInstanceRepository.getAppInstances(tokenRepository.getAllClientsForSession(sidToken.getId()))) {
      if (otherAppInstance == null) {
        // that shouldn't happen, but we don't want to break if that's the case
        continue;
      }
      // TODO: I18N
      otherApps.add(otherAppInstance.getName().get(Locale.ROOT));
    }
    // TODO: I18N: we should sort according to the user's locale
    Collections.sort(otherApps, Collator.getInstance(Locale.ROOT));
    viewModel.put(LogoutSoyTemplateInfo.OTHER_APPS, new SoyListData(otherApps));
    viewModel.put(LogoutSoyTemplateInfo.IS_PORTAL, appInstance != null && appInstance.getId().equals(ClientIds.PORTAL));
    // FIXME: this should probably be a different URL, make it configurable
    viewModel.put(LogoutSoyTemplateInfo.PORTAL_URL, settings.landingPage == null ? "" : settings.landingPage.toString());
    return Response.ok(new SoyTemplate(LogoutSoyInfo.LOGOUT, viewModel)).build();
  }

  private Response redirectTo(@Nullable URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(settings.landingPage, uriInfo);
    }
    return Response.seeOther(continueUrl).build();
  }

  @VisibleForTesting
  @Nullable
  private IdToken.Payload parseIdTokenHint(@Nullable String idTokenHint, @Nullable SidToken sidToken) {
    return parseIdTokenHint(jsonFactory, settings.keyPair.getPublic(), Resteasy1099.getBaseUri(uriInfo).toString(),
        idTokenHint, sidToken);
  }

  @VisibleForTesting
  @Nullable
  static IdToken.Payload parseIdTokenHint(JsonFactory jsonFactory, PublicKey publicKey, String issuer,
      @Nullable String idTokenHint, @Nullable SidToken sidToken) {
    if (idTokenHint == null) {
      return null;
    }
    try {
      IdToken idToken = IdToken.parse(jsonFactory, idTokenHint);
      if (!idToken.verifySignature(publicKey)) {
        logger.debug("Bad signature for id_token_hint: {}", idTokenHint);
        return null;
      }
      if (!idToken.verifyIssuer(issuer)) {
        logger.debug("Bad issuer for id_token_hint (expected: {}, actual: {})", issuer, idToken.getPayload().getIssuer());
        return null;
      }
      IdToken.Payload payload = idToken.getPayload();
      if (payload.getAudience() == null) {
        logger.debug("Missing audience in id_token_hint: {}", idTokenHint);
        return null;
      }
      // there must be an audience
      assert payload.getAudienceAsList() != null && !payload.getAudienceAsList().isEmpty();
      if (sidToken != null && !sidToken.getAccountId().equals(payload.getSubject())) {
        // The app asked to sign-out another session (we'll assume the user already signed out –and
        // signed in again– but the app didn't caught it up)
        logger.debug("Mismatching subject in id_token_hint (expected: {}, actual: {})",
            sidToken.getAccountId(), payload.getSubject());
        return null;
      }
      return payload;
    } catch (Exception e) {
      logger.debug("Error parsing id_token_hint", e);
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
