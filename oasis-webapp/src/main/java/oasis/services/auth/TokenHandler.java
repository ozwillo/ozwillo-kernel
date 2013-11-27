package oasis.services.auth;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.Instant;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.OneTimeToken;
import oasis.model.accounts.RefreshToken;
import oasis.model.accounts.Token;

public class TokenHandler {

  @Inject
  JongoTokenRepository tokenRepository;

  /**
   * Remove expired tokens
   */
  public void cleanUpTokens(Account account) {
    checkNotNull(account);

    List<Token> removeList = new ArrayList<>();

    // Process each token to see if it is valid
    for(Token token : this.getAllTokens(account)) {
      // We process only valid tokens
      if (token == null) {
        continue;
      }

      // Check is the token is not valid
      if (!this.checkTokenValidity(account, token)) {
        // Add the TokenId to the removeList
        removeList.add(token);
      }
    }

    tokenRepository.revokeTokens(account, removeList.toArray(new Token[removeList.size()]));
  }

  public List<Token> getAllTokens(Account account) {
    checkNotNull(account);

    return account.getTokens();
  }

  public boolean checkTokenValidity(Account account, Token token) {
    checkNotNull(account);

    // A null token is not valid !
    if ( token == null ) {
      return false;
    }

    // Compute the token Expiration Date
    Instant tokenExpirationDate = Instant.now().plus(token.getTimeToLive());
    if (tokenExpirationDate.isBefore(Instant.now())) {
      // Token is expired
      return false;
    }

    // Check if the current user know the token
    return this.getAllTokens(account).contains(token);
  }

  public AccessToken createAccessToken(Account account) {
    return this.createAccessToken(account, 3600, null);
  }

  public AccessToken createAccessToken(Account account, long ttl, RefreshToken refreshToken) {
    checkNotNull(account);

    AccessToken newAccessToken = new AccessToken();

    newAccessToken.setCreationTime(Instant.now().toDate());
    newAccessToken.setTimeToLive(ttl);

    if ( refreshToken != null ) {
      newAccessToken.setRefreshTokenId(refreshToken.getId());
    }

    tokenRepository.registerToken(account, newAccessToken);

    // Return the new access token
    return newAccessToken;
  }

  public OneTimeToken createOneTimeAccessToken(Account account) {
    OneTimeToken newOneTimeToken = new OneTimeToken();

    newOneTimeToken.setCreationTime(Instant.now().toDate());
    // A OneTimeToken is available only for 60 seconds
    newOneTimeToken.setTimeToLive(60);

    // Register the new access token in memory
    tokenRepository.registerToken(account, newOneTimeToken);

    // Return the new token
    return newOneTimeToken;
  }

  public RefreshToken createRefreshToken(Account account) {
    RefreshToken refreshToken = new RefreshToken();

    refreshToken.setCreationTime(Instant.now().toDate());
    // 100 years
    refreshToken.setTimeToLive(100*365*24*3600);

    tokenRepository.registerToken(account, refreshToken);

    return refreshToken;
  }

  /**
   * Use a OneTimeToken to create a new AccessToken
   * @return a one time token
   */
  public AccessToken oneTimeTokenToAccessToken(Account account, OneTimeToken oneTimeToken) {
    // We must have a valid token to create a new AccessToken
    checkArgument(checkTokenValidity(account, oneTimeToken));

    tokenRepository.revokeToken(account, oneTimeToken);

    return createAccessToken(account);
  }

  public void revokeToken(Account account, Token token) {
    tokenRepository.revokeToken(account, token);
  }
}
