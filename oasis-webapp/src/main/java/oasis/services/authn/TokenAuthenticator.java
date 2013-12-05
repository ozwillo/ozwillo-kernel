package oasis.services.authn;

import javax.inject.Inject;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.CredentialExpiredException;
import javax.security.auth.login.LoginException;

import com.google.common.base.Preconditions;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Token;

public class TokenAuthenticator {
  private final TokenHandler tokenHandler;
  private final AccountRepository accountRepository;

  @Inject TokenAuthenticator(TokenHandler tokenHandler, AccountRepository accountRepository) {
    this.tokenHandler = tokenHandler;
    this.accountRepository = accountRepository;
  }

  public Account authenticate(Token token) throws LoginException {
    Preconditions.checkArgument(token != null && token.getId() != null);

    // Check if we find an account containing the specified token
    Account account = accountRepository.getAccountByToken(token);

    if (account == null) {
      throw new AccountNotFoundException();
    }

    // Check if token is still valid
    if (!tokenHandler.checkTokenValidity(account, token)) {
      throw new CredentialExpiredException();
    }

    return account;
  }
}
