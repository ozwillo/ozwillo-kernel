package oasis.web.providers;

import com.google.common.base.CharMatcher;
import java.util.Date;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
 * Implementation of a {@link HeaderDelegate} for {@link NewCookie} that follows RFC 6265.
 * <p>
 * Works around the fact the JAX-RS is built around RFC 2109 that nobody ever implemented.
 *
 * @see <a href="http://tools.ietf.org/html/rfc6265">RFC 6265</a>
 * @see <a href="https://java.net/jira/browse/JAX_RS_SPEC-430">Bug report on the JAX-RS spec</a>
 */
@ConstrainedTo(RuntimeType.SERVER)
public class NewCookieHeaderDelegate implements HeaderDelegate<NewCookie> {
  private static final HeaderDelegate<Date> DATE_HEADER_DELEGATE = RuntimeDelegate.getInstance().createHeaderDelegate(Date.class);

  @Override
  public NewCookie fromString(String value) {
    // Not needed on server side; Set-Cookie should never be sent to the server
    // Note: that rules out use of NewCookie.valueOf()
    throw new UnsupportedOperationException();
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
