package oasis.model.auth;

import oasis.model.accounts.Account;
import oasis.model.accounts.Token;

public interface TokenRepository {
  public void registerToken(Account account, Token token);

  public void revokeToken(Account account, Token token);

  public void revokeTokens(Account account, Token[] tokens);
}
