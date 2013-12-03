package oasis.model.authn;

import oasis.model.accounts.Token;

public interface TokenRepository {
  public Token getToken(String tokenId);

  public boolean registerToken(String accountId, Token token);

  public boolean revokeToken(String accountId, Token token);
}
