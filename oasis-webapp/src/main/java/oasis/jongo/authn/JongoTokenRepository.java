/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.jongo.authn;

import static com.google.common.base.Preconditions.*;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.auth.AuthModule;
import oasis.jongo.JongoBootstrapper;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;

public class JongoTokenRepository implements TokenRepository, JongoBootstrapper {
  private final Jongo jongo;
  private final AuthModule.Settings settings;

  @Inject JongoTokenRepository(Jongo jongo, AuthModule.Settings settings) {
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

  @Override
  public boolean registerToken(Token token) {
    token.checkValidity();

    try {
      this.getTokensCollection()
          .insert(token);
    } catch (DuplicateKeyException e) {
      return false;
    }

    return true;
  }

  @Override
  public boolean revokeToken(String tokenId) {
    checkArgument(!Strings.isNullOrEmpty(tokenId));

    return this.getTokensCollection()
        .remove("{ $or: [ { id: # }, { ancestorIds: # } ] }", tokenId, tokenId)
        .getN() > 0;
  }

  @Override
  public SidToken renewSidToken(String tokenId, boolean usingClientCertificate) {
    Instant expirationTime = Instant.now().plus(settings.sidTokenDuration);

    return this.getTokensCollection().findAndModify("{ id: # }", tokenId)
        .returnNew()
        // TODO: Pass directly the instance of Instant
        .with(usingClientCertificate ? "{ $set: { expirationTime: #, usingClientCertificate:# } }"        : "{ $set: { expirationTime: # }, $unset: { usingClientCertificate: 1 } }",
              usingClientCertificate ? new Object[]{ Date.from(expirationTime), usingClientCertificate }  : new Object[]{ Date.from(expirationTime) })
        .as(SidToken.class);
  }

  @Override
  public boolean reAuthSidToken(String tokenId) {
    Instant authenticationTime = Instant.now();

    WriteResult writeResult = this.getTokensCollection()
        .update("{ id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { authenticationTime: # } }", Date.from(authenticationTime));

    return writeResult.getN() > 0;
  }

  @Override
  public boolean reAuthSidToken(String tokenId, String franceconnectIdToken) {
    Instant authenticationTime = Instant.now();

    WriteResult writeResult = this.getTokensCollection()
        .update("{ id: # }", tokenId)
        // TODO: Pass directly the instance of Instant
        .with("{ $set: { authenticationTime: #, franceconnectIdToken: # } }", Date.from(authenticationTime), franceconnectIdToken);

    return writeResult.getN() > 0;
  }

  @Override
  public int revokeTokensForAccount(String accountId) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    return this.getTokensCollection()
        .remove("{ accountId: # }", accountId)
        .getN();
  }

  @Override
  public int revokeTokensForAccountAndTokenType(String accountId, Class<? extends Token> tokenType) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(tokenType);

    return this.getTokensCollection()
        .remove("{ accountId: #, _type: # }", accountId,
            // FIXME: this only works because all our token classes are in the same package
            "." + tokenType.getSimpleName())
        .getN();
  }

  @Override
  public int revokeTokensForClient(String clientId) {
    checkArgument(!Strings.isNullOrEmpty(clientId));

    return this.getTokensCollection()
        .remove("{ serviceProviderId: # }", clientId)
        .getN();
  }

  @Override
  public int revokeTokensForScopes(Collection<String> scopeIds) {
    if (scopeIds.isEmpty()) {
      return 0;
    }
    return this.getTokensCollection()
        .remove("{ scopeIds: { $in: # } }", ImmutableSet.copyOf(scopeIds))
        .getN();
  }

  @Override
  public int revokeInvitationTokensForOrganizationMembership(String organizationMembershipId) {
    checkArgument(!Strings.isNullOrEmpty(organizationMembershipId));

    return this.getTokensCollection()
        .remove("{ organizationMembershipId: # }", organizationMembershipId)
        .getN();
  }

  @Override
  public int revokeInvitationTokensForAppInstance(String aceId) {
    checkArgument(!Strings.isNullOrEmpty(aceId));

    return this.getTokensCollection()
        .remove("{ aceId: # }", aceId)
        .getN();
  }

  @Override
  public Collection<String> getAllClientsForSession(String sidTokenId) {
    checkArgument(!Strings.isNullOrEmpty(sidTokenId));
    return getTokensCollection()
        .distinct("serviceProviderId")
        .query("{ ancestorIds: # }", sidTokenId)
        .as(String.class);
  }

  @Override
  public void bootstrap() {
    getTokensCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getTokensCollection().ensureIndex("{ ancestorIds: 1 }");
    getTokensCollection().ensureIndex("{ accountId: 1 }");
    getTokensCollection().ensureIndex("{ expirationTime: 1 }", "{ background: 1, expireAfterSeconds: 0 }");
  }
}
