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
package oasis.services.authn;

import javax.inject.Inject;

import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;
import oasis.services.authn.login.PasswordHasher;
import oasis.services.authn.login.SCryptPasswordHasher;

public class CredentialsService {
  private final CredentialsRepository credentialsRepository;
  private final PasswordHasher passwordHasher;

  @Inject
  CredentialsService(CredentialsRepository credentialsRepository, SCryptPasswordHasher passwordHasher) {
    this.credentialsRepository = credentialsRepository;
    this.passwordHasher = passwordHasher;
  }

  public void setPassword(ClientType type, String id, String password) {
    byte[] salt = passwordHasher.createSalt();
    byte[] hash = passwordHasher.hashPassword(password, salt);
    credentialsRepository.saveCredentials(type, id, hash, salt);
  }

  public boolean checkPassword(ClientType type, String id, String password) {
    Credentials credentials = credentialsRepository.getCredentials(type, id);
    if (credentials == null) {
      return false;
    }
    return passwordHasher.checkPassword(password, credentials.getHash(), credentials.getSalt());
  }
}
