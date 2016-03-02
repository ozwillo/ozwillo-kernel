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

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;

import de.thetaphi.forbiddenapis.SuppressForbidden;

class CookieDateParser {
  private static final CharMatcher DELIMITER = CharMatcher.is((char) 0x09)
      .or(CharMatcher.inRange((char) 0x20, (char) 0x2F))
      .or(CharMatcher.inRange((char) 0x3B, (char) 0x40))
      .or(CharMatcher.inRange((char) 0x5B, (char) 0x60))
      .or(CharMatcher.inRange((char) 0x7B, (char) 0x7E))
      .precomputed();
  private static final CharMatcher ALPHA = CharMatcher.inRange('A', 'Z')
      .or(CharMatcher.inRange('a', 'z'));
  private static final CharMatcher DIGIT = CharMatcher.inRange('0', '9');
  private static final CharMatcher NON_DELIMITER = DELIMITER.negate().precomputed();

  private static final ImmutableList<String> MONTHS = ImmutableList.of(
      "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

  public static Date parseCookieDate(String cookieDate) {
    return new CookieDateParser(cookieDate).parse();
  }

  private final String cookieDate;

  private boolean foundTime, foundDayOfMonth, foundMonth, foundYear;
  private int year, month, dayOfMonth, hour, minute, second;
  private int start, end;
  private char cur;

  private CookieDateParser(String cookieDate) {
    this.cookieDate = cookieDate;
  }

  private boolean nextDateToken() {
    assert start <= end;
    start = NON_DELIMITER.indexIn(cookieDate, end);
    if (start < 0) {
      return false;
    }
    end = DELIMITER.indexIn(cookieDate, start);
    if (end < start) {
      end = cookieDate.length();
    }
    updateCur();
    return true;
  }

  private boolean available() {
    return start < end;
  }

  private boolean available(int chars) {
    return start + chars <= end;
  }

  private char peek() {
    assert available();
    return cur;
  }

  private boolean is(char c) {
    return available() && cur == c;
  }

  private boolean isAlpha() {
    return available() && ALPHA.matches(cur);
  }

  private boolean isDigit() {
    return available() && DIGIT.matches(cur);
  }

  private void consume(char c) {
    assert peek() == c;
    start++;
    updateCur();
  }

  private String read(int length) {
    int s = start;
    start = Math.min(start + length, end);
    updateCur();
    return cookieDate.substring(s, start);
  }

  private void updateCur() {
    cur = available() ? cookieDate.charAt(start) : 0;
  }

  private int tryReadDigits(int minLength, int maxLength) {
    int mark = start;

    assert minLength > 0;
    assert maxLength >= minLength;
    assert available(minLength);

    int lowEnd = start + minLength;
    int highEnd = start + maxLength;

    int result = 0;
    for (; isDigit() && start < highEnd; start++, updateCur()) {
      result = result * 10 + (peek() - '0');
    }
    if (start < lowEnd) {
      // reset pointer back to original position
      start = mark;
      updateCur();
      return -1;
    }
    return result;
  }

  @SuppressWarnings("deprecation") // Date.UTC
  @SuppressForbidden
  public Date parse() {
    parseTokens();

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

  private void parseTokens() {
    while (nextDateToken()) {
      if (!foundMonth && isAlpha()) {
        month = MONTHS.indexOf(Ascii.toLowerCase(read(3)));
        if (month >= 0) {
          foundMonth = true;
        }
      } else if (isDigit() &&
          ((!foundTime && available(5))
              || !foundDayOfMonth
              || (!foundYear && available(2)))) {
        int hourOrDay = tryReadDigits(1, 2);
        if (hourOrDay < 0) {
          continue;
        }
        if (foundTime || !is(':')) {
          // if the next char is not a digit, then it could be either a day-of-month or a year
          if (!isDigit()) {
            // day-of-month has precedence over year
            if (!foundDayOfMonth) {
              foundDayOfMonth = true;
              dayOfMonth = hourOrDay;
            } else if (!foundYear) {
              foundYear = true;
              year = hourOrDay;
            }
          } else if (!foundYear) {
            int mark = start;
            int y = tryReadDigits(1, 2);
            assert y >= 0;
            if (!isDigit()) { // must not have too many digits
              foundYear = true;
              year = (hourOrDay * (start - mark == 1 ? 10 : 100)) + y;
            }
          }
          continue;
        }
        consume(':');
        hour = hourOrDay;
        minute = tryReadDigits(1, 2);
        if (minute < 0 || !is(':')) {
          continue;
        }
        consume(':');
        second = tryReadDigits(1, 2);
        if (second < 0 || isDigit()) {
          continue;
        }
        foundTime = true;
        continue;
      }
      if (!foundYear && available(2)) { // year is between 2 and 4 chars long
        year = tryReadDigits(2, 4);
        if (year >= 0 && !isDigit()) {
          foundYear = true;
        }
      }
    }
  }
}
