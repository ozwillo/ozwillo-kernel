package oasis.http.fixes;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

public abstract class AbstractCookieDateParserTest {
  protected static Iterable<Object[]> data() throws IOException {
    ObjectMapper mapper = new ObjectMapper(new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS));
    JsonNode examples = mapper.readTree(AbstractCookieDateParserTest.class.getResource("/http-cache/tests/data/dates/examples.json"));
    JsonNode bsdExamples = mapper.readTree(AbstractCookieDateParserTest.class.getResource("/http-cache/tests/data/dates/bsd-examples.json"));
    return FluentIterable.from(Iterables.concat(examples, bsdExamples))
        .transform(new Function<JsonNode, Object[]>() {
          @Override
          public Object[] apply(JsonNode input) {
            return new Object[] {
                input.get("test").textValue(),
                input.get("expected").textValue()
            };
          }
        });
  }

  private static final SimpleDateFormat rfc1123date;
  static {
    rfc1123date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    rfc1123date.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final String input;
  private final String expected;

  protected AbstractCookieDateParserTest(String input, @Nullable String expected) {
    this.input = input;
    this.expected = expected;
  }

  @Test
  public void testParseCookieDate() {
    Date actual = parseCookieDate(input);
    // XXX: do not use DateAssert#withDateFormat as it stores the format in a static field
    // also parses 'expected' rather than formatting 'actual'.
    assertThat(format(actual)).isEqualTo(expected);
  }

  protected abstract Date parseCookieDate(String cookieDate);

  private static String format(Date date) {
    if (date == null) return null;
    return rfc1123date.format(date);
  }
}
