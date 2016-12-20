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
package oasis.services.authn.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.inject.Inject;

import org.bouncycastle.crypto.generators.SCrypt;

public class SCryptPasswordHasher implements PasswordHasher {
  // Taken from http://stackoverflow.com/a/12581268/116472
  // XXX: make configurable? (would need to be stored in the hashes/salts.
  private static final int N = 16384; // 2^14
  private static final int r = 8;
  private static final int p = 1;

  private static final int dkLen = 32; // XXX: make configurable?

  private final SecureRandom secureRandom;

  @Inject
  SCryptPasswordHasher(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  @Override
  public byte[] createSalt() {
    byte[] salt = new byte[32];
    secureRandom.nextBytes(salt);
    return salt;
  }

  @Override
  public byte[] hashPassword(String password, byte[] salt) {
    return hashPassword(password, salt, dkLen);
  }

  @Override
  public boolean checkPassword(String password, byte[] hash, byte[] salt) {
    // Compute a hash of the same length as the correct hash.
    byte[] testHash = hashPassword(password, salt, hash.length);
    return MessageDigest.isEqual(hash, testHash);
  }

  private byte[] hashPassword(String password, byte[] salt, int dkLen) {
    return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, N, r, p, dkLen);
  }
}
