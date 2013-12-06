package oasis.services.authn.login;

import java.security.GeneralSecurityException;

public interface PasswordHasher {
  byte[] createSalt();

  byte[] hashPassword(String password, byte[] salt) throws GeneralSecurityException;

  boolean checkPassword(String password, byte[] hash, byte[] salt);
}
