package oasis.http.fixes;

import java.io.IOException;
import java.util.Date;

import javax.annotation.Nullable;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CookieDateRegexpParserTest extends AbstractCookieDateParserTest {
  @Parameterized.Parameters(name = "{index}: parseCookieDate({0}) = {1}")
  public static Iterable<Object[]> data() throws IOException {
    return AbstractCookieDateParserTest.data();
  }

  public CookieDateRegexpParserTest(String input, @Nullable String expected) {
    super(input, expected);
  }

  protected Date parseCookieDate(String cookieDate) {
    return CookieDateRegexpParser.parseCookieDate(cookieDate);
  }
}

