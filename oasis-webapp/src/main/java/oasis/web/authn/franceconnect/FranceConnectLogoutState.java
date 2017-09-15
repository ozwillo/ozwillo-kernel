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

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.annotation.Nullable;
import javax.ws.rs.core.NewCookie;

import org.immutables.value.Value;

import com.google.common.io.BaseEncoding;
import com.google.common.net.UrlEscapers;

import oasis.services.cookies.CookieFactory;

@Value.Immutable
public abstract class FranceConnectLogoutState {
  private static final String COOKIE_NAME_PREFIX = "franceconnect_logout_state-";
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  // Note: make sure BASE_ENCODING (used in generateRandom) won't ever produce such a character
  private static final String SEPARATOR = "/";

  public static String generateStateKey(SecureRandom secureRandom) {
    byte[] bytes = new byte[16]; // 128bits
    secureRandom.nextBytes(bytes);
    return BASE_ENCODING.encode(bytes);
  }

  static String getCookieName(String stateKey, boolean secure) {
    return CookieFactory.getCookieName(COOKIE_NAME_PREFIX + stateKey, secure);
  }

  public static NewCookie createCookie(String stateKey, @Nullable String instanceId, @Nullable String post_logout_redirect_uri, @Nullable String state, boolean secure) {
    return CookieFactory.createSessionCookie(COOKIE_NAME_PREFIX + stateKey, toString(instanceId, post_logout_redirect_uri, state), secure, true);
  }

  static NewCookie createExpiredCookie(String stateKey, boolean secure) {
    return CookieFactory.createExpiredCookie(COOKIE_NAME_PREFIX + stateKey, secure, true);
  }

  @Nullable
  static FranceConnectLogoutState parse(String serialized) {
    if (serialized == null) {
      return null;
    }
    serialized = new String(BASE_ENCODING.decode(serialized), StandardCharsets.UTF_8);
    int idx1 = serialized.indexOf(SEPARATOR);
    if (idx1 < 0) {
      return null;
    }
    int idx2 = serialized.indexOf(SEPARATOR, idx1 + 1);
    if (idx2 < 0) {
      return null;
    }
    try {
      return ImmutableFranceConnectLogoutState.builder()
          .instanceId(emptyToNull(serialized.substring(0, idx1)))
          .post_logout_redirect_uri(emptyToNull(serialized.substring(idx2 + 1)))
          .state(emptyToNull(URLDecoder.decode(serialized.substring(idx1 + 1, idx2), "UTF-8")))
          .build();
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  @Nullable abstract String instanceId();
  @Nullable abstract String post_logout_redirect_uri();
  @Nullable abstract String state();

  @Override
  public String toString() {
    return toString(instanceId(), post_logout_redirect_uri(), state());
  }

  private static String toString(@Nullable String instanceId, @Nullable String post_logout_redirect_uri, @Nullable String state) {
    return BASE_ENCODING.encode(
        (nullToEmpty(instanceId)
            + SEPARATOR
            + (state == null ? "" : UrlEscapers.urlPathSegmentEscaper().escape(state))
            + SEPARATOR
            + nullToEmpty(post_logout_redirect_uri)
        ).getBytes(StandardCharsets.UTF_8));
  }
}
