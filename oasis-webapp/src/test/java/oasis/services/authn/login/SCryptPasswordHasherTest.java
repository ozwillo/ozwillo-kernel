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
package oasis.services.authn.login;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class SCryptPasswordHasherTest {
  protected SCryptPasswordHasher sCryptPasswordHasher;

  @Before
  public void init() {
    sCryptPasswordHasher = new SCryptPasswordHasher(new SecureRandom());
  }

  @Test
  public void testSamePassword() throws GeneralSecurityException {
    String password = "This is a test";
    byte[] salt = sCryptPasswordHasher.createSalt();

    // Test password hash against himself (random salt)
    byte[] hash = sCryptPasswordHasher.hashPassword(password, salt);

    assertTrue(sCryptPasswordHasher.checkPassword(password, hash, salt));
  }

  @Test
  public void testDifferentSalt() throws GeneralSecurityException {
    String password = "This is a test";
    byte[] salt = sCryptPasswordHasher.createSalt();
    byte[] salt2 = sCryptPasswordHasher.createSalt();
    assertFalse(Arrays.equals(salt, salt2));

    // Test if hash with different salts are different
    assertFalse(Arrays.equals(
        sCryptPasswordHasher.hashPassword(password, salt),
        sCryptPasswordHasher.hashPassword(password, salt2)));
  }

  @Test
  public void testDifferentPassword() throws GeneralSecurityException {
    String password = "This is a test";
    String password2 = "This is a test, the return";
    byte[] salt = sCryptPasswordHasher.createSalt();

    // Test if hash with different pass are different
    assertFalse(Arrays.equals(
        sCryptPasswordHasher.hashPassword(password, salt),
        sCryptPasswordHasher.hashPassword(password2, salt)));
  }
}
