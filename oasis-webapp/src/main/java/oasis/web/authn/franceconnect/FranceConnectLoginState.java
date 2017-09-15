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
package oasis.web.authn.franceconnect;

import oasis.services.cookies.CookieFactory;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.ws.rs.core.NewCookie;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.google.common.io.BaseEncoding;
import com.ibm.icu.util.ULocale;

@Value.Immutable
abstract class FranceConnectLoginState {
  private static final String COOKIE_NAME_PREFIX = "franceconnect_state-";
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  // Note: make sure BASE_ENCODING (used in generateRandom) won't ever produce such a character
  private static final String SEPARATOR = "/";

  static String generateStateKey(SecureRandom secureRandom) {
    return generateRandom(secureRandom);
  }

  static String generateNonce(SecureRandom secureRandom) {
    return generateRandom(secureRandom);
  }

  private static String generateRandom(SecureRandom secureRandom) {
    byte[] bytes = new byte[16]; // 128bits
    secureRandom.nextBytes(bytes);
    return BASE_ENCODING.encode(bytes);
  }

  static String getCookieName(String state, boolean secure) {
    return CookieFactory.getCookieName(COOKIE_NAME_PREFIX + state, secure);
  }

  static NewCookie createCookie(String state, ULocale locale, String nonce, URI continueUrl, boolean secure) {
    return CookieFactory.createSessionCookie(COOKIE_NAME_PREFIX + state, toString(locale, nonce, continueUrl), secure, true);
  }

  static NewCookie createExpiredCookie(String state, boolean secure) {
    return CookieFactory.createExpiredCookie(COOKIE_NAME_PREFIX + state, secure, true);
  }

  @Nullable
  static FranceConnectLoginState parse(String serialized) {
    if (serialized == null) {
      return null;
    }
    serialized = new String(BASE_ENCODING.decode(serialized), StandardCharsets.UTF_8);
    int idx1 = serialized.indexOf(SEPARATOR);
    if (idx1 < 1) {
      return null;
    }
    int idx2 = serialized.indexOf(SEPARATOR, idx1 + 1);
    if (idx2 < 0) {
      return null;
    }
    return ImmutableFranceConnectLoginState.builder()
        // XXX: use ULocale.Builder#setLanguageTag for stricter parsing?
        .locale(ULocale.forLanguageTag(serialized.substring(0, idx1)))
        .nonce(serialized.substring(idx1 + 1, idx2))
        // XXX: use same logic as in UriParamConverter? use galimatias as in OriginHelper?
        .continueUrl(URI.create(serialized.substring(idx2 + 1)))
        .build();
  }

  abstract ULocale locale();
  abstract String nonce();
  abstract URI continueUrl();

  @Override
  public String toString() {
    return toString(locale(), nonce(), continueUrl());
  }

  private static String toString(ULocale locale, String nonce, URI continueUrl) {
    return BASE_ENCODING.encode((locale.toLanguageTag() + SEPARATOR + nonce + SEPARATOR + continueUrl.toString()).getBytes(StandardCharsets.UTF_8));
  }
}
