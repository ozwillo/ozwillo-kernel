package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Strings;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Token;
import oasis.model.authn.TokenRepository;

public class JongoTokenRepository implements TokenRepository {
  private final Jongo jongo;

  private final AccountRepository accountRepository;

  @Inject JongoTokenRepository(Jongo jongo, AccountRepository accountRepository) {
    this.jongo = jongo;
    this.accountRepository = accountRepository;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Token getToken(String tokenId) {
    checkArgument(!Strings.isNullOrEmpty(tokenId));

    Account account = accountRepository.getAccountByTokenId(tokenId);

    for(Token t : account.getTokens()) {
      if (t.getId().equals(tokenId)) {
        return t;
      }
    }

    return null;
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
        .with("{ $pull: { tokens: { $or: [ { id: # }, { refreshTokenId: # } ] } } }", tokenId, tokenId)
        .getN() > 0;
  }
}
