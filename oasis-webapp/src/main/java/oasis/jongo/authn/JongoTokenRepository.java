package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.model.accounts.Account;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;

public class JongoTokenRepository implements TokenRepository {
  private final Jongo jongo;

  @Inject JongoTokenRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  public void registerToken(Account account, Token token) {
    checkNotNull(account);

    // Register the new access token in memory
    account.addToken(token);

    // Add the new access token in mongo
    this.getAccountCollection().update("{id:#}", account.getId()).with("{$push:{tokens:#}}", token);
  }

  public void revokeToken(Account account, Token token) {
    checkArgument(account != null && token.getId() == null && token.getId().isEmpty());

    // Remove the access token
    account.removeToken(token);

    // Remove the token in mongo
    this.getAccountCollection().update("{id:#}", account.getId()).with("{$pull:{tokens:{id:#}}}", token.getId());
  }

  public void revokeTokens(Account account, Token[] tokens) {
    checkNotNull(account);

    if (tokens.length == 0) {
      return;
    }

    // Get only token ids
    List<String> removeList = new ArrayList<>(tokens.length);

    for (Token token : tokens) {
      account.removeToken(token);
      removeList.add(token.getId());
    }

    // Remove tokens from mongo
    this.getAccountCollection().update("{id:#}", account.getId()).with("{$pullAll:{tokens:{id:#}}}", removeList.toArray());
  }
}
