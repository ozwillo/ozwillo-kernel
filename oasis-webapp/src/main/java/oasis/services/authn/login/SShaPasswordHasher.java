package oasis.services.authn.login;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class SShaPasswordHasher implements PasswordHasher {
  static final HashFunction shaFunc = Hashing.sha1();

  public byte[] createSalt() {
    final Random r = new SecureRandom();

    byte[] salt = new byte[32];
    r.nextBytes(salt);

    return salt;
  }

  @Override
  public byte[] hashPassword(String password, byte[] salt) throws GeneralSecurityException {
    // Can't hash without a salt
    Preconditions.checkArgument(salt != null && salt.length > 0);

    // Get the SHA Hasher
    Hasher shaHasher = shaFunc.newHasher();

    // Compute the hash
    shaHasher.putBytes(password.getBytes(StandardCharsets.UTF_8));
    shaHasher.putBytes(salt);

    // Base64 encode the digest
    return shaHasher.hash().asBytes();
  }

  @Override
  public boolean checkPassword(String password, byte[] hash, byte[] salt) {
    // Compute given password's hash using user's salt
    Hasher shaHasher = shaFunc.newHasher();
    shaHasher.putBytes(password.getBytes(StandardCharsets.UTF_8));
    shaHasher.putBytes(salt);

    // Get the SHA-1 hash
    byte[] computedHash = shaHasher.hash().asBytes();

    // Compare stored hash and computed hash
    return MessageDigest.isEqual(hash, computedHash);
  }
}
