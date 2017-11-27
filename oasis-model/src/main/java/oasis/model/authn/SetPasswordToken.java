/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetPasswordToken extends AbstractAccountToken {
  @JsonProperty
  private byte[] pwdhash;
  @JsonProperty
  private byte[] pwdsalt;

  public byte[] getPwdhash() {
    return pwdhash;
  }

  public void setPwdhash(byte[] pwdhash) {
    this.pwdhash = pwdhash;
  }

  public byte[] getPwdsalt() {
    return pwdsalt;
  }

  public void setPwdsalt(byte[] pwdsalt) {
    this.pwdsalt = pwdsalt;
  }
}
