package oasis.services.auth;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.OneTimeToken;
import oasis.model.accounts.Token;

/**
 * Token manager using Jongo as backend storage
 * TODO : Split this class into a JongoTokenRepository and a TokenHandler.
 */
public class JongoTokenHandler {
  @Inject
  Jongo jongo;

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  protected void registerToken(Account account, Token token) {
    checkNotNull(account);

    // Register the new access token in memory
    account.addToken(token);

    // Add the new access token in mongo
    this.getAccountCollection().update("{id:#}", account.getId()).with("{$push:{tokens:#}}", token);
  }

  /**
   * Remove expired tokens
   */
  public void cleanUpTokens(Account account) {
    checkNotNull(account);

    List<String> removeList = new ArrayList<>();

    // Process each token to see if it is valid
    for(Token token : this.getAllTokens(account)) {
      // We process only valid tokens
      if (token == null) {
        continue;
      }

      // Check is the token is not valid
      if (!this.checkTokenValidity(account, token)) {
        // Add the TokenId to the removeList
        removeList.add(token.getId());

        // Remove the token in memory
        account.removeToken(token);
      }
    }

    // If we have to remove tokens, use a mongo query
    if ( !removeList.isEmpty() ) {
      this.getAccountCollection().update("{id:#}", account.getId()).with("{$pullAll:{tokens:{id:#}}}", removeList.toArray());
    }
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
    return this.createAccessToken(account, 3600);
  }

  public AccessToken createAccessToken(Account account, long ttl) {
    checkNotNull(account);

    AccessToken newAccessToken = new AccessToken();

    newAccessToken.setCreationTime(Instant.now().toDate());
    newAccessToken.setTimeToLive(ttl);

    this.registerToken(account, newAccessToken);

    // Return the new access token
    return newAccessToken;
  }

  public OneTimeToken createOneTimeAccessToken(Account account) {
    OneTimeToken newOneTimeToken = new OneTimeToken();

    newOneTimeToken.setCreationTime(Instant.now().toDate());
    // A OneTimeToken is available only for 60 seconds
    newOneTimeToken.setTimeToLive(60);

    // Register the new access token in memory
    this.registerToken(account, newOneTimeToken);

    // Return the new token
    return newOneTimeToken;
  }

  /**
   * Use a OneTimeToken to create a new AccessToken
   * @return a one time token
   */
  public AccessToken oneTimeTokenToAccessToken(Account account, OneTimeToken oneTimeToken) {
    // We must have a valid token to create a new AccessToken
    checkArgument(checkTokenValidity(account, oneTimeToken));

    this.revokeToken(account, oneTimeToken);

    return createAccessToken(account);
  }

  public void revokeToken(Account account, Token token) {
    checkArgument(account != null && token.getId() == null && token.getId().isEmpty());

    // Check if the token is a valid token (trying to revoke an invalid token is useless)
    if ( !this.checkTokenValidity(account, token) ) {
      return;
    }

    // Remove the access token
    account.removeToken(token);

    // Remove the token in mongo
    this.getAccountCollection().update("{id:#}", account.getId()).with("{$pull:{tokens:{id:#}}}", token.getId());
  }
}
