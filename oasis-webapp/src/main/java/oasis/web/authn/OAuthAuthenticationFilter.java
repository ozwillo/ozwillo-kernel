package oasis.web.authn;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import oasis.model.authn.AccessToken;
import oasis.services.authn.TokenHandler;

/**
 * Implements Bearer Token authentication.
 *
 * @see <a href='http://tools.ietf.org/html/rfc6750'>OAuth 2.0 Bearer Token Usage</a>
 */
@Authenticated @OAuth
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OAuthAuthenticationFilter implements ContainerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(OAuthAuthenticationFilter.class);
  private static final String AUTH_SCHEME = "Bearer";
  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

  @Context ResourceInfo resourceInfo;
  @Inject TokenHandler tokenHandler;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      challenge(requestContext);
      return;
    }

    List<String> parts = AUTH_SCHEME_SPLITTER.splitToList(authorization);

    if (parts.isEmpty() || !AUTH_SCHEME.equalsIgnoreCase(parts.get(0))) {
      challenge(requestContext);
      return;
    }
    if (parts.size() != 2) {
      invalidRequest(requestContext);
      return;
    }

    AccessToken accessToken = tokenHandler.getCheckedToken(parts.get(1), AccessToken.class);
    if (accessToken == null) {
      invalidToken(requestContext);
      return;
    }

    /** Check if the resource have an annotation {@link WithScopes} **/
    WithScopes withScopesAnnotation = resourceInfo.getResourceMethod().getAnnotation(WithScopes.class);
    if (withScopesAnnotation == null) {
      withScopesAnnotation = resourceInfo.getResourceClass().getAnnotation(WithScopes.class);
      if (withScopesAnnotation == null) {
        logger.error("Resource requires OAuth token but doesn't declare required scopes: {}", resourceInfo);
        // XXX: should we send a forbidden response rather than let the request go in?
      }
    }
    if (withScopesAnnotation != null && !accessToken.getScopeIds().containsAll(Arrays.asList(withScopesAnnotation.value()))) {
      insufficientScope(requestContext);
      return;
    }

    final OAuthPrincipal accountPrincipal = new OAuthPrincipal(accessToken);
    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();

    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public OAuthPrincipal getUserPrincipal() {
        return accountPrincipal;
      }

      @Override
      public boolean isUserInRole(String role) {
        return false;
      }

      @Override
      public boolean isSecure() {
        return oldSecurityContext.isSecure();
      }

      @Override
      public String getAuthenticationScheme() {
        return "OAUTH_BEARER";
      }
    });
  }

  private void challenge(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME)
        .build());
  }

  private void invalidRequest(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_request\"")
        .build());
  }

  private void insufficientScope(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.FORBIDDEN)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"insufficient_scope\"")
        .build());
  }

  private void invalidToken(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_token\"")
        .build());
  }
}
