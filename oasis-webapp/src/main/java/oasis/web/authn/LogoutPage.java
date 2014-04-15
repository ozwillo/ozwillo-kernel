package oasis.web.authn;

import java.net.URI;
import java.security.PublicKey;

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
import com.google.common.collect.ImmutableMap;

import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.ServiceProvider;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
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
  @Inject ApplicationRepository applicationRepository;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @Nullable @QueryParam("id_token_hint") String id_token_hint,
      @Nullable @QueryParam("post_logout_redirect_uri") URI post_logout_redirect_uri,
      @Nullable @QueryParam(LoginPage.CONTINUE_PARAM) URI continueUrl) {
    // legacy: if OIDC Session is not used, then possibly use continueUrl
    // FIXME: remove this, as it can be used as an open redirector, and log the user out with CSRF
    if (id_token_hint == null && post_logout_redirect_uri == null && continueUrl != null) {
      return (securityContext.getUserPrincipal() != null)
          ? post(continueUrl)
          : redirectTo(continueUrl);
    }

    final SidToken sidToken = (securityContext.getUserPrincipal() != null)
        ? ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken()
        : null;

    final IdToken.Payload idTokenHint = parseIdTokenHint(id_token_hint, sidToken);
    final ServiceProvider serviceProvider;

    if (idTokenHint != null) {
      final String client_id = idTokenHint.getAudienceAsList().get(0);
      serviceProvider = applicationRepository.getServiceProvider(client_id);
    } else {
      serviceProvider = null;
    }

    if (serviceProvider != null) {
      // TODO: validate post_logout_redirect_uri against serviceProvider
    } else {
      // don't act as an open redirector!
      post_logout_redirect_uri = null;
    }

    if (securityContext.getUserPrincipal() == null) {
      // Not authenticated (we'll assume the user already signed out but the app didn't caught it up)
      return redirectTo(post_logout_redirect_uri);
    }

    ImmutableMap.Builder<String, Object> viewModel = ImmutableMap.builder();
    viewModel.put("formAction", UriBuilder.fromResource(LogoutPage.class).build());
    if (post_logout_redirect_uri != null) {
      viewModel.put("continue", post_logout_redirect_uri);
    }
    if (serviceProvider != null) {
      viewModel.put("app", ImmutableMap.of(
          "name", serviceProvider.getName()
      ));
    }
    return Response.ok(new View(LogoutPage.class, "Logout.html", viewModel.build())).build();
  }

  private Response redirectTo(@Nullable URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(uriInfo);
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
        // there must be an audience
        assert payload.getAudienceAsList() != null && !payload.getAudienceAsList().isEmpty();
        return null;
      }
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
      continueUrl = LoginPage.defaultContinueUrl(uriInfo);
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
