package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import java.util.Set;

import javax.inject.Inject;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.google.common.base.Strings;

import oasis.model.accounts.AbstractOAuthToken;
import oasis.model.accounts.AccessToken;
import oasis.model.accounts.AuthorizationCode;
import oasis.model.accounts.RefreshToken;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;

public class TokenHandler {
  private final TokenRepository tokenRepository;

  @Inject TokenHandler(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  public boolean checkTokenValidity(Token token) {
    // A null token is not valid !
    if (token == null) {
      return false;
    }

    // Compute the token Expiration Date
    Instant tokenExpirationDate = token.getCreationTime().plus(token.getTimeToLive());
    if (tokenExpirationDate.isBefore(Instant.now())) {
      // Token is expired
      return false;
    }

    // The token is valid
    return true;
  }

  public AccessToken createAccessToken(String accountId, AuthorizationCode authorizationCode) {
    return this.createAccessToken(accountId, Duration.standardHours(1), authorizationCode);
  }

  public AccessToken createAccessToken(String accountId, RefreshToken refreshToken) {
    return this.createAccessToken(accountId, Duration.standardHours(1), refreshToken);
  }

  private AccessToken createAccessToken(String accountId, Duration ttl, AbstractOAuthToken token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AccessToken newAccessToken = new AccessToken();

    newAccessToken.setCreationTime(Instant.now());
    newAccessToken.setTimeToLive(ttl);
    newAccessToken.setScopeIds(token.getScopeIds());
    newAccessToken.setServiceProviderId(token.getServiceProviderId());

    if (token != null) {
      if (token instanceof AuthorizationCode) {
        if (!tokenRepository.revokeToken(token.getId())) {
          return null;
        }
      } else if (token instanceof RefreshToken) {
        newAccessToken.setRefreshTokenId(token.getId());
      }
    }

    if (!registerToken(accountId, newAccessToken)) {
      return null;
    }

    // Return the new access token
    return newAccessToken;
  }

  public AuthorizationCode createAuthorizationCode(String accountId, Set<String> scopeIds, String serviceProviderId) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AuthorizationCode newAuthorizationCode = new AuthorizationCode();

    newAuthorizationCode.setCreationTime(Instant.now());
    // A AuthorizationCode is available only for 1 minute
    newAuthorizationCode.setTimeToLive(Duration.standardMinutes(1));
    newAuthorizationCode.setScopeIds(scopeIds);
    newAuthorizationCode.setServiceProviderId(serviceProviderId);

    // Register the new access token in memory
    if (!registerToken(accountId, newAuthorizationCode)) {
      return null;
    }

    // Return the new token
    return newAuthorizationCode;
  }

  public boolean registerToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(token);

    return tokenRepository.registerToken(accountId, token);
  }
}
