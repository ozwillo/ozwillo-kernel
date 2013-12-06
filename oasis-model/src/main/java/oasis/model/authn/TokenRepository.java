package oasis.model.authn;

import oasis.model.accounts.Token;

public interface TokenRepository {
  Token getToken(String tokenId);

  boolean registerToken(String accountId, Token token);

  boolean revokeToken(String accountId, Token token);
}
