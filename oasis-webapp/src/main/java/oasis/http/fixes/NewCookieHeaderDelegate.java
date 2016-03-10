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
package oasis.http.fixes;

import java.util.Date;
import java.util.Iterator;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;

/**
 * Implementation of a {@link HeaderDelegate} for {@link NewCookie} that follows RFC 6265.
 * <p>
 * Works around the fact the JAX-RS is built around RFC 2109 that nobody ever implemented.
 *
 * @see <a href="http://tools.ietf.org/html/rfc6265">RFC 6265</a>
 * @see <a href="https://java.net/jira/browse/JAX_RS_SPEC-430">Bug report on the JAX-RS spec</a>
 */
public class NewCookieHeaderDelegate implements HeaderDelegate<NewCookie> {
  private static final HeaderDelegate<Date> DATE_HEADER_DELEGATE = RuntimeDelegate.getInstance().createHeaderDelegate(Date.class);

  private static final CharMatcher WSP = CharMatcher.anyOf(" \t");
  private static final Splitter ATTRIBUTES = Splitter.on(';');
  private static final Splitter COOKIE_AV = Splitter.on('=').limit(2).trimResults(WSP);

  @Override
  public NewCookie fromString(String newCookie) {
    Iterator<String> attributes = ATTRIBUTES.split(newCookie).iterator();

    // The first pair is the cookie-name / cookie-value
    Iterator<String> cookieAv = COOKIE_AV.split(attributes.next()).iterator();
    String name = cookieAv.next();
    if (name.isEmpty()) {
      return null;
    }
    if (!cookieAv.hasNext()) {
      // no '=' sign
      return null;
    }
    String value = cookieAv.next();

    if (!attributes.hasNext()) {
      return new NewCookie(name, value);
    }

    // parse the attributes
    Date expiry = null;
    int maxAge = NewCookie.DEFAULT_MAX_AGE;
    String domain = null;
    String path = null;
    boolean secure = false;
    boolean httpOnly = false;

    do {
      cookieAv = COOKIE_AV.split(attributes.next()).iterator();
      String attributeName = cookieAv.next();
      String attributeValue = Iterators.getNext(cookieAv, "");
      if ("Expires".equalsIgnoreCase(attributeName)) {
        expiry = parseCookieDate(attributeValue);
      } else if ("Max-Age".equalsIgnoreCase(attributeName)) {
        try {
          // Note: deviation from spec: accepts a leading '+' sign
          // Normalize all negative values to 0, to avoid conflicts with DEFAULT_MAX_AGE
          maxAge = Math.max(0, Integer.parseInt(attributeValue));
        } catch (NumberFormatException nfe) {
        }
      } else if ("Domain".equalsIgnoreCase(attributeName)) {
        if (attributeValue.isEmpty()) {
          continue;
        }
        if (attributeValue.startsWith(".")) {
          attributeValue = attributeValue.substring(1);
        }
        domain = Ascii.toLowerCase(attributeValue);
      } else if ("Path".equalsIgnoreCase(attributeName)) {
        if (attributeValue.isEmpty() || !attributeValue.startsWith("/")) {
          path = null; // reset to default path
        } else {
          path = attributeValue;
        }
      } else if ("Secure".equalsIgnoreCase(attributeName)) {
        secure = true;
      } else if ("HttpOnly".equalsIgnoreCase(attributeName)) {
        httpOnly = true;
      }
    } while (attributes.hasNext());

    return new NewCookie(name, value, path, domain, Cookie.DEFAULT_VERSION, null/*comment*/, maxAge, expiry, secure, httpOnly);
  }

  /**
   * Parse a cookie date following the
   * <a href="http://tools.ietf.org/html/rfc6265#section-5.1.1">RFC 6265 algorithm</a>.
   *
   * @return the parsed date or {@code null} in case of error
   */
  private Date parseCookieDate(String cookieDate) {
    return CookieDateParser.parseCookieDate(cookieDate);
  }

  @Override
  public String toString(NewCookie cookie) {
    StringBuilder builder = new StringBuilder();

    builder.append(validateCookieName(cookie));
    builder.append('=');
    builder.append(validateCookieValue(cookie));

    if (cookie.getExpiry() != null) {
      builder.append("; Expires=");
      builder.append(DATE_HEADER_DELEGATE.toString(cookie.getExpiry()));
    }
    if (cookie.getMaxAge() != NewCookie.DEFAULT_MAX_AGE) {
      // XXX: issue warning if value is <= 0 ?
      builder.append("; Max-Age=");
      builder.append(cookie.getMaxAge());
    }
    if (cookie.getDomain() != null) {
      // TODO: validate
      builder.append("; Domain=");
      builder.append(cookie.getDomain());
    }
    if (cookie.getPath() != null) {
      // TODO: validate
      builder.append("; Path=");
      builder.append(cookie.getPath());
    }
    if (cookie.isSecure()) {
      builder.append("; Secure");
    }
    if (cookie.isHttpOnly()) {
      builder.append("; HttpOnly");
    }
    return builder.toString();
  }

  private static final CharMatcher SEPARATORS = CharMatcher.anyOf("()<>@,;:\\\"/[]?={} \t");
  private static final CharMatcher TOKEN = CharMatcher.inRange((char) 32, (char) 126).and(SEPARATORS.negate()).precomputed();

  private static String validateCookieName(NewCookie cookie) {
    String cookieName = cookie.getName();
    if (!TOKEN.matchesAllOf(cookieName)) {
      throw new IllegalArgumentException("Cookie name contains illegal characters: " + cookieName);
    }
    return cookieName;
  }

  private static final CharMatcher COOKIE_OCTET = CharMatcher.is((char) 0x21)
      .or(CharMatcher.inRange((char) 0x23, (char) 0x2B))
      .or(CharMatcher.inRange((char) 0x2D, (char) 0x3A))
      .or(CharMatcher.inRange((char) 0x3C, (char) 0x5B))
      .or(CharMatcher.inRange((char) 0x5D, (char) 0x7E))
      .precomputed();

  private static String validateCookieValue(NewCookie cookie) {
    String cookieValue = cookie.getValue();
    if (cookieValue == null) {
      return "";
    }
    if (!COOKIE_OCTET.matchesAllOf(cookieValue)) {
      throw new IllegalArgumentException("Cookie value contains illegal characters: " + cookieValue);
    }
    return cookieValue;
  }
}
