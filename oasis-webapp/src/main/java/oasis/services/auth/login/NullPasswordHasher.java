package oasis.services.auth.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class NullPasswordHasher implements PasswordHasher {

  @Override
  public byte[] createSalt() {
    return null;
  }

  @Override
  public byte[] hashPassword(String password, byte[] salt) {
    return password.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public boolean checkPassword(String password, byte[] hash, byte[] salt) {
    return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), hash);
  }
}
