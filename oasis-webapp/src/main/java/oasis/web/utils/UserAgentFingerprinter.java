package oasis.web.utils;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class UserAgentFingerprinter {

  // Let's use the fastest algorithm to start with. Maybe switch to MurMur3 later for better uniqueness.
  private static final HashFunction HASH_FUNCTION = Hashing.adler32();

  private static final String[] FINGERPRINTED_HEADERS = {
      HttpHeaders.USER_AGENT,
      HttpHeaders.ACCEPT,
      HttpHeaders.ACCEPT_CHARSET,
      HttpHeaders.ACCEPT_ENCODING,
      HttpHeaders.ACCEPT_LANGUAGE,
  };

  public byte[] fingerprint(ContainerRequestContext ctx) {
    Hasher hasher = HASH_FUNCTION.newHasher();
    for (String name : FINGERPRINTED_HEADERS) {
      String value = ctx.getHeaderString(name);
      if (value != null) {
        hasher.putString(value, StandardCharsets.UTF_8);
      }
    }
    return hasher.hash().asBytes();
  }

  public byte[] fingerprint(HttpHeaders ctx) {
    Hasher hasher = HASH_FUNCTION.newHasher();
    for (String name : FINGERPRINTED_HEADERS) {
      String value = ctx.getHeaderString(name);
      if (value != null) {
        hasher.putString(value, StandardCharsets.UTF_8);
      }
    }
    return hasher.hash().asBytes();
  }
}
