package oasis.services.authn;

import javax.inject.Inject;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;

public class UserPasswordAuthenticator {
  protected final AccountRepository accountRepository;
  protected final CredentialsService credentialsService;

  @Inject
  UserPasswordAuthenticator(
      AccountRepository accountRepository,
      CredentialsService credentialsService) {
    this.accountRepository = accountRepository;
    this.credentialsService = credentialsService;
  }

  public UserAccount authenticate(String email, String password) throws LoginException {
    // Get the user account matching the given email
    UserAccount userAccount = accountRepository.getUserAccountByEmail(email);

    // Check if we found a user
    if (userAccount == null) {
      throw new AccountNotFoundException();
    }

    // Check password using a defined PasswordHasher
    if (!credentialsService.checkPassword(ClientType.USER, userAccount.getId(), password)) {
      throw new FailedLoginException();
    }

    // Password match, authn is a success, return the Account object
    return userAccount;
  }

  public void setPassword(String accountId, String password) {
    credentialsService.setPassword(ClientType.USER, accountId, password);
  }
}
