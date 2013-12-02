package oasis.web.authn;

import java.io.IOException;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.security.auth.login.LoginException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Splitter;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.Token;
import oasis.services.auth.TokenAuthenticator;
import oasis.services.auth.TokenHandler;
import oasis.services.auth.TokenSerializer;

/**
 * Implements Bearer Token authentication.
 *
 * @see <a href='http://tools.ietf.org/html/rfc6750'>OAuth 2.0 Bearer Token Usage</a>
 */
@Authenticated @OAuth
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OAuthAuthenticationFilter implements ContainerRequestFilter {
  private static final String AUTH_SCHEME = "Bearer";
  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

  @Inject TokenAuthenticator tokenAuthenticator;
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

    Token token;
    try {
      token = TokenSerializer.unserialize(parts.get(1));
    } catch (Exception e) {
      invalidToken(requestContext);
      return;
    }

    Account account;
    try {
      account = tokenAuthenticator.authenticate(token);
    } catch (LoginException e) {
      invalidToken(requestContext);
      return;
    }

    AccessToken accessToken = getAccessTokenFromAccount(account, token.getId()); // Can't trust the received token

    if (!tokenHandler.checkTokenValidity(account, accessToken)) {
      invalidToken(requestContext);
      return;
    }

    if (accessToken == null) {
      invalidToken(requestContext);
      return;
    }

    // TODO: load account lazily
    final OAuthPrincipal accountPrincipal = new OAuthPrincipal(account, accessToken);
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

  private AccessToken getAccessTokenFromAccount(Account account, String tokenId) {
    for (Token t : account.getTokens()) {
      if (t.getId().equals(tokenId)) {
        if (t instanceof AccessToken) {
          return (AccessToken) t;
        }
        return null;
      }
    }
    return null;
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

  private void invalidToken(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_token\"")
        .build());
  }
}
