package oasis.services.auth;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.CredentialExpiredException;
import javax.security.auth.login.LoginException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import oasis.model.accounts.Account;
import oasis.model.accounts.Token;
import oasis.services.accounts.JongoAccountRepository;

public class TokenAuthenticator {
  @Inject
  TokenHandler tokenHandler;

  @Inject
  JongoAccountRepository accountRepository;

  public Account authenticate(Token token) throws LoginException {
    Preconditions.checkArgument(token != null && token.getId() != null);

    // Check if we find an account containing the specified token
    Account account = accountRepository.getAccountByToken(token);

    if ( account == null ) {
      throw new AccountNotFoundException();
    }

    // Check if token is still valid
    if ( !tokenHandler.checkTokenValidity(account, token) ) {
      throw new CredentialExpiredException();
    }

    return account;
  }
}
