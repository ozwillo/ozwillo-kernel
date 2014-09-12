package oasis.services.authn;

import java.security.SecureRandom;

import javax.inject.Inject;

import com.google.common.io.BaseEncoding;

// FIXME: we need a truly secure password generator; see https://www.grc.com/passwords.htm
public class PasswordGenerator {
  private final SecureRandom secureRandom;

  @Inject PasswordGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String generate() {
    byte[] bytes = new byte[32]; // 256 bits
    secureRandom.nextBytes(bytes);
    return BaseEncoding.base64().encode(bytes);
  }
}
