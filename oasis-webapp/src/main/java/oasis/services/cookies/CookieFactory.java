package oasis.services.cookies;

import java.util.Date;
import java.util.TimeZone;

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
