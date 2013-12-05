package oasis.services.authn.login;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class SShaPasswordHasherTest {
  protected SShaPasswordHasher sShaPasswordHasher;

  @Before
  public void init() {
    sShaPasswordHasher = new SShaPasswordHasher();
  }

  @Test
  public void testSamePassword() throws GeneralSecurityException {
    String password = "This is a test";
    byte[] salt = "This a salt".getBytes(StandardCharsets.UTF_8);

    // Test password hash against himself (random salt)
    byte[] hash = sShaPasswordHasher.hashPassword(password, salt);

    assertTrue(sShaPasswordHasher.checkPassword(password, hash, salt));
  }

  @Test
  public void testDifferentSalt() throws GeneralSecurityException {
    String password = "This is a test";
    byte[] salt = "This a salt".getBytes(StandardCharsets.UTF_8);
    byte[] salt2 = "This a salt again".getBytes(StandardCharsets.UTF_8);

    // Test if hash with different salts are different
    assertNotEquals(
        sShaPasswordHasher.hashPassword(password, salt),
        sShaPasswordHasher.hashPassword(password, salt2));
  }

  @Test
  public void testDifferentPassword() throws GeneralSecurityException {
    String password = "This is a test";
    String password2 = "This is a test, the return";
    byte[] salt = "This a salt".getBytes(StandardCharsets.UTF_8);

    // Test if hash with different pass are different
    assertNotEquals(
        sShaPasswordHasher.hashPassword(password, salt),
        sShaPasswordHasher.hashPassword(password2, salt));
  }
}
