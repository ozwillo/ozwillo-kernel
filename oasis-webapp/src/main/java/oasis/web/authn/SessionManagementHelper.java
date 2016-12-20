/**
 * Ozwillo Kernel
 * Copyright (C) 2016  The Ozwillo Kernel Authors
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
package oasis.web.authn;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import oasis.services.cookies.CookieFactory;
import oasis.services.security.OriginHelper;

public class SessionManagementHelper {
  // XXX: keep in sync with check_session_iframe.html
  public static final String COOKIE_NAME = "BS"; // BS stands for Browser State

  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  private static final Joiner DOT_JOINER = Joiner.on('.');
  private static final Joiner SPACE_JOINER = Joiner.on(' ');

  public static NewCookie createBrowserStateCookie(boolean secure, String browserState) {
    return CookieFactory.createSessionCookie(COOKIE_NAME, browserState, secure, false);
  }

  private final SecureRandom secureRandom;

  @Inject
  public SessionManagementHelper(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String generateBrowserState() {
    return generateRandom();
  }

  public String computeSessionState(String client_id, String redirect_uri, String browserState) {
    final String salt = generateRandom();
    return DOT_JOINER.join(
        BASE_ENCODING.encode(Hashing.sha256()
            .hashString(SPACE_JOINER.join(
                client_id,
                OriginHelper.originFromUri(redirect_uri),
                browserState,
                salt),
                StandardCharsets.UTF_8)
            .asBytes()),
        salt);
  }

  private String generateRandom() {
    byte[] bytes = new byte[16]; // 128bits
    secureRandom.nextBytes(bytes);
    return BASE_ENCODING.encode(bytes);
  }
}
