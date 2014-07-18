package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Strings;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
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

  protected MongoCollection getTokensCollection() {
    return jongo.getCollection("tokens");
  }

  @Override
  public Token getToken(String tokenId) {
    return getTokensCollection()
        .findOne("{ id: # }", tokenId)
        .as(Token.class);
  }

  public boolean registerToken(Token token) {
    checkArgument(!Strings.isNullOrEmpty(token.getAccountId()));

    WriteResult writeResult = this.getTokensCollection()
        .insert(token);

    return true;
  }

  public boolean revokeToken(String tokenId) {
    checkArgument(!Strings.isNullOrEmpty(tokenId));

    return this.getTokensCollection()
        .remove("{ $or: [ { id: # }, { ancestorIds: # } ] }", tokenId, tokenId)
        .getN() > 0;
  }

  public boolean renewToken(String tokenId) {
    Instant expirationTime = Instant.now().plus(settings.sidTokenDuration);

    WriteResult writeResult = this.getTokensCollection()
        .update("{ id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { expirationTime: # } }", expirationTime.getMillis());

    return writeResult.getN() > 0;
  }

  @Override
  public boolean reAuthSidToken(String tokenId) {
    Instant authenticationTime = Instant.now();

    WriteResult writeResult = this.getTokensCollection()
        .update("{ id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { authenticationTime: # } }", authenticationTime.getMillis());

    return writeResult.getN() > 0;
  }

  @Override
  public void bootstrap() {
    getTokensCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getTokensCollection().ensureIndex("{ expirationTime: 1 }", "{ background: 1, expireAfterSeconds: 0 }");
  }
}
