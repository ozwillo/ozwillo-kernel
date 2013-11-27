package oasis.services.auth;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.CredentialExpiredException;
import javax.security.auth.login.CredentialNotFoundException;
import javax.security.auth.login.LoginException;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import oasis.model.accounts.Account;
import oasis.model.accounts.Token;

public class JongoTokenAuthenticator {
  @Inject
  JongoTokenHandler jongoTokenHandler;

  @Inject
  Jongo jongo;

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  public Account authenticate(Token token) throws LoginException {
    Preconditions.checkArgument(token != null && token.getId() != null);

    // Check if we find an account containing the specified token
    Account account = this.getAccountCollection().findOne("{tokens.id:#}", token.getId()).as(Account.class);

    if ( account == null ) {
      throw new AccountNotFoundException();
    }

    // Check if token is still valid
    if ( !jongoTokenHandler.checkTokenValidity(account, token) ) {
      throw new CredentialExpiredException();
    }

    // TODO : Implement token checkup
    throw new CredentialNotFoundException();
  }
}
