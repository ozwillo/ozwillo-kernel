package oasis.http.fixes;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

@VisibleForTesting
class CookieDateRegexpParser {
  private static final CharMatcher DELIMITER = CharMatcher.is((char) 0x09)
      .or(CharMatcher.inRange((char) 0x20, (char) 0x2F))
      .or(CharMatcher.inRange((char) 0x3B, (char) 0x40))
      .or(CharMatcher.inRange((char) 0x5B, (char) 0x60))
      .or(CharMatcher.inRange((char) 0x7B, (char) 0x7E))
      .precomputed();

  private static final Pattern DAY_OF_MONTH = Pattern.compile("\\d{1,2}(?!\\d)");
  private static final ImmutableList MONTHS = ImmutableList.of(
      "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
  private static final Pattern YEAR = Pattern.compile("\\d{2,4}(?!\\d)");
  private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})(?!\\d)");

  /**
   * Parse a cookie date following the
   * <a href="http://tools.ietf.org/html/rfc6265#section-5.1.1">RFC 6265 algorithm</a>.
   *
   * @return the parsed date or {@code null} in case of error
   */
  @SuppressWarnings("deprecation")
  public static Date parseCookieDate(String cookieDate) {
    boolean foundTime = false, foundDayOfMonth = false, foundMonth = false, foundYear = false;
    int year = 0, month = 0, dayOfMonth = 0, hour = 0, minute = 0, second = 0;
    for (String dateToken : Splitter.on(DELIMITER).omitEmptyStrings().split(cookieDate)) {
      if (!foundTime) {
        Matcher matcher = TIME.matcher(dateToken);
        if (matcher.lookingAt()) {
          foundTime = true;
          hour = Integer.parseInt(matcher.group(1));
          minute = Integer.parseInt(matcher.group(2));
          second = Integer.parseInt(matcher.group(3));
          continue;
        }
      }
      if (!foundDayOfMonth) {
        Matcher matcher = DAY_OF_MONTH.matcher(dateToken);
        if (matcher.lookingAt()) {
          foundDayOfMonth = true;
          dayOfMonth = Integer.parseInt(matcher.group());
          continue;
        }
      }
      if (!foundMonth && dateToken.length() >= 3) {
        month = MONTHS.indexOf(Ascii.toLowerCase(dateToken.substring(0, 3)));
        if (month >= 0) {
          foundMonth = true;
          continue;
        }
      }
      if (!foundYear) {
        Matcher matcher = YEAR.matcher(dateToken);
        if (matcher.lookingAt()) {
          foundYear = true;
          year = Integer.parseInt(matcher.group());
          continue;
        }
      }
    }
    if (!foundTime || !foundDayOfMonth || !foundMonth || !foundYear) {
      return null;
    }
    if (70 <= year && year <= 99) {
      year += 1900;
    } else if (0 <= year && year <= 69) {
      year += 2000;
    }
    if (dayOfMonth < 1 || 31 < dayOfMonth) {
      return null;
    }
    if (year < 1601) {
      return null;
    }
    if (hour > 23) {
      return null;
    }
    if (minute > 59) {
      return null;
    }
    if (second > 59) {
      return null;
    }
    return new Date(Date.UTC(year - 1900, month, dayOfMonth, hour, minute, second));
  }
}
