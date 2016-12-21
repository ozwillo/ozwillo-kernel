/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.model.authn;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SidToken extends AbstractAccountToken {
  @JsonProperty
  private Instant authenticationTime;

  @JsonProperty
  private byte[] userAgentFingerprint;

  @JsonProperty
  private boolean usingClientCertificate;

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

  public boolean isUsingClientCertificate() {
    return usingClientCertificate;
  }

  public void setUsingClientCertificate(boolean usingClientCertificate) {
    this.usingClientCertificate = usingClientCertificate;
  }
}
