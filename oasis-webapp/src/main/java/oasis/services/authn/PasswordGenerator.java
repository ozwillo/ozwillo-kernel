/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
    return BaseEncoding.base64().omitPadding().encode(bytes);
  }
}
