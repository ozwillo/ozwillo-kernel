package oasis.services.authn.login;

import java.security.GeneralSecurityException;

public interface PasswordHasher {
  public byte[] createSalt();

  public byte[] hashPassword(String password, byte[] salt) throws GeneralSecurityException;

  public boolean checkPassword(String password, byte[] hash, byte[] salt);
}
