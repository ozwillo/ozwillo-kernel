package oasis.model.authn;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SidToken extends AbstractAccountToken {
  @JsonProperty
  private Instant authenticationTime;

  @JsonProperty
  private byte[] userAgentFingerprint;

  /**
   * Gets the last time the user authenticated by providing their credentials.
   *
   * <p>This is different from {@link #getCreationTime()} as the user might have
   * been asked to re-authenticate during the session lifetime; or the session
   * might have been created from a <i>remember me</i> cookie.
   */
  public Instant getAuthenticationTime() {
    return authenticationTime;
  }

  public void setAuthenticationTime(Instant authenticationTime) {
    this.authenticationTime = authenticationTime;
  }

  public byte[] getUserAgentFingerprint() {
    return userAgentFingerprint;
  }

  public void setUserAgentFingerprint(byte[] userAgentFingerprint) {
    this.userAgentFingerprint = userAgentFingerprint;
  }
}
