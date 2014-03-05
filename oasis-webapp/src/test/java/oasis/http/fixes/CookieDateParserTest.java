package oasis.http.fixes;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.assertj.core.api.Condition;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

@RunWith(Parameterized.class)
public class CookieDateParserTest extends AbstractCookieDateParserTest {
  @Parameters(name = "{index}: parseCookieDate({0}) = {1}")
  public static Iterable<Object[]> data() throws IOException {
    return AbstractCookieDateParserTest.data();
  }

  public CookieDateParserTest(String input, @Nullable String expected) {
    super(input, expected);
  }

  @Override
  protected Date parseCookieDate(String cookieDate) {
    return CookieDateParser.parseCookieDate(cookieDate);
  }
}
