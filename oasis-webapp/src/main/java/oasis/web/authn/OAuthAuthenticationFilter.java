package oasis.web.authn;

import java.io.IOException;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
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
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;

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

  @Inject AccountRepository accountRepository;
  @Inject TokenRepository tokenRepository;
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

    Account account = accountRepository.getAccountByTokenId(token.getId());

    if (account == null) {
      invalidToken(requestContext);
      return;
    }

    // Get real information for the token
    token = tokenRepository.getToken(token.getId());

    if (token == null || !(token instanceof AccessToken) || !tokenHandler.checkTokenValidity(token)) {
      invalidToken(requestContext);
      return;
    }

    // TODO: load account lazily
    final OAuthPrincipal accountPrincipal = new OAuthPrincipal(account, (AccessToken) token);
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

  private void invalidToken(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .header(HttpHeaders.WWW_AUTHENTICATE, AUTH_SCHEME + " error=\"invalid_token\"")
        .build());
  }
}
