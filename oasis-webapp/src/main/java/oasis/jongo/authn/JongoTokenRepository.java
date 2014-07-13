package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Strings;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.accounts.Account;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;

public class JongoTokenRepository implements TokenRepository, JongoBootstrapper {
  private final Jongo jongo;
  private final OpenIdConnectModule.Settings settings;

  @Inject JongoTokenRepository(Jongo jongo, OpenIdConnectModule.Settings settings) {
    this.jongo = jongo;
    this.settings = settings;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Token getToken(String tokenId) {
    Account account = getAccountCollection()
        .findOne("{tokens.id: #}", tokenId)
        .projection("{tokens.$: 1, type: 1, id: 1}")
        .as(Account.class);

    if (account == null || account.getTokens() == null || account.getTokens().isEmpty()) {
      return null;
    }
    Token token = account.getTokens().get(0);
    token.setAccountId(account.getId());
    return token;
  }

  public boolean registerToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    token.setAccountId(accountId);

    WriteResult writeResult = this.getAccountCollection()
        .update("{ id: # }", accountId)
        .with("{ $push: { tokens: # } }", token);

    return writeResult.getN() > 0;
  }

  public boolean revokeToken(String tokenId) {
    checkArgument(!Strings.isNullOrEmpty(tokenId));

    return this.getAccountCollection()
        .update("{ tokens.id: # }", tokenId)
        .with("{ $pull: { tokens: { $or: [ { id: # }, { ancestorIds: # } ] } } }", tokenId, tokenId)
        .getN() > 0;
  }

  public boolean renewToken(String tokenId) {
    Instant expirationTime = Instant.now().plus(settings.sidTokenDuration);

    WriteResult writeResult = this.getAccountCollection()
        .update("{ tokens.id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { tokens.$.expirationTime: # } }", expirationTime.getMillis());

    return writeResult.getN() == 1;
  }

  @Override
  public boolean reAuthSidToken(String tokenId) {
    Instant authenticationTime = Instant.now();

    WriteResult writeResult = this.getAccountCollection()
        .update("{ tokens.id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { tokens.$.authenticationTime: # } }", authenticationTime.getMillis());

    return writeResult.getN() == 1;
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ tokens.id: 1 }", "{ unique: 1, sparse: 1 }");
  }
}
