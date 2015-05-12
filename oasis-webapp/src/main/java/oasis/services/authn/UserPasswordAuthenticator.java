/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
