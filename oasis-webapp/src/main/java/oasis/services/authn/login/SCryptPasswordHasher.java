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
