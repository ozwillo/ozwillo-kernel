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
package oasis.services.cookies;

import java.util.Date;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class CookieFactory {
  private static final Date FAR_PAST = new DateTime(2008, 1, 20, 11, 10, DateTimeZone.forID("Europe/Paris")).toDate();

  public static NewCookie createCookie(String cookieName, String value, int maxAge, Date expires, boolean secure) {
    return new NewCookie(
        cookieName,                             // name
        value,                                  // value
        "/",                                    // path
        null,                                   // domain
        Cookie.DEFAULT_VERSION,                 // version
        null,                                   // comment
        maxAge,                                 // max-age
        expires,                                // expiry
        secure,                                 // secure
        true                                    // http-only
    );
  }

  public static NewCookie createSessionCookie(String cookieName, String value, boolean secure) {
    return createCookie(cookieName, value, NewCookie.DEFAULT_MAX_AGE, null, secure);
  }

  public static NewCookie createExpiredCookie(String cookieName, boolean secure) {
    return createCookie(cookieName, null, NewCookie.DEFAULT_MAX_AGE, FAR_PAST, secure);
  }
}
