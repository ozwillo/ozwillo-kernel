package oasis.services.authn;

import javax.inject.Inject;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import com.google.common.io.BaseEncoding;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.services.authn.login.PasswordHasher;
import oasis.services.authn.login.SShaPasswordHasher;

public class UserPasswordAuthenticator {
  protected final AccountRepository accountRepository;

  protected final PasswordHasher passwordHasher;

  static final BaseEncoding base64Encoder = BaseEncoding.base64();

  @Inject
  UserPasswordAuthenticator(
      AccountRepository accountRepository,
      SShaPasswordHasher sShaPasswordHasher) {
    this.accountRepository = accountRepository;
    this.passwordHasher = sShaPasswordHasher;
  }

  public Account authenticate(String email, String password) throws LoginException {

    // Get the user account matching the given email
    UserAccount userAccount = accountRepository.getUserAccountByEmail(email);

    // Check if we found a user
    if (userAccount == null) {
      throw new AccountNotFoundException();
    }

    // Check password using a defined PasswordHasher
    if (!this.passwordHasher.checkPassword(
        password,
        base64Encoder.decode(userAccount.getPassword()),
        base64Encoder.decode(userAccount.getPasswordSalt()))) {
      throw new FailedLoginException();
    }

    // Password match, authn is a success, return the Account object
    return userAccount;
  }
}
