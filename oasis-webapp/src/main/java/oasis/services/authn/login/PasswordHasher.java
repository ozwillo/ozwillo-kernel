package oasis.services.authn.login;

public interface PasswordHasher {
  byte[] createSalt();

  byte[] hashPassword(String password, byte[] salt);

  boolean checkPassword(String password, byte[] hash, byte[] salt);
}
