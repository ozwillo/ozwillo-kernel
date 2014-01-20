package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Strings;

import oasis.model.accounts.Account;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;

public class JongoTokenRepository implements TokenRepository {
  private final Jongo jongo;

  @Inject JongoTokenRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Token getToken(String tokenId) {
    Account account = getAccountCollection()
        .findOne("{tokens.id: #}", tokenId)
        .projection("{tokens.$: 1, type: 1}")
        .as(Account.class);

    if (account == null || account.getTokens() == null || account.getTokens().isEmpty()) {
      return null;
    }
    return account.getTokens().get(0);
  }

  public boolean registerToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(token);

    // Add the new access token in mongo
    return this.getAccountCollection().update("{id:#},{$ne: {tokens.id:#}", accountId, token.getId()).with("{$push:{tokens:#}}", token).getN() > 0;
  }

  public boolean revokeToken(String tokenId) {
    checkArgument(!Strings.isNullOrEmpty(tokenId));

    return this.getAccountCollection()
        .update("{ tokens.id: # }", tokenId)
        .with("{ $pull: { tokens: { $or: [ { id: # }, { ancestorIds: # } ] } } }", tokenId, tokenId)
        .getN() > 0;
  }
}
