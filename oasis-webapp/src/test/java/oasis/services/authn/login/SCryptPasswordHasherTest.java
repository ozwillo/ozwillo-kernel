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
