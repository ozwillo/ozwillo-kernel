package oasis.http.fixes;

import com.google.caliper.Benchmark;
import com.google.caliper.runner.CaliperMain;

/**
 * Benchmarks implementations of parseCookieDate.
 *
 * <p>Run with: <pre>
 *   java -cp caliper-1.0-beta-1-all.jar:oasis-webapp/target/classes/:oasis-webapp/target/test-classes/ oasis.http.fixes.CookieDateParserBenchmark
 * </pre>
 *
 * <p><a href="https://microbenchmarks.appspot.com/runs/1dd7690f-ea0b-408c-b143-03d3ab028c9c">Sample results</a>
 */
public class CookieDateParserBenchmark extends Benchmark {
  public static void main(String[] args) throws Exception {
    CaliperMain.main(CookieDateParserBenchmark.class, args);
  }

  private static final String COOKIE_DATE_DMYT = "Mon, 10 Dec 2007 17:02:24 GMT";

  public void timeRegexp_DMYT(int reps) {
    for (int i = 0; i < reps; i++) {
      CookieDateRegexpParser.parseCookieDate(COOKIE_DATE_DMYT);
    }
  }

  public void timeParser_DMYT(int reps) {
    for (int i = 0; i < reps; i++) {
      CookieDateParser.parseCookieDate(COOKIE_DATE_DMYT);
    }
  }

  private static final String COOKIE_DATE_YMDT = "2017 April 15 21:01:22";

  public void timeRegexp_YMDT(int reps) {
    for (int i = 0; i < reps; i++) {
      CookieDateRegexpParser.parseCookieDate(COOKIE_DATE_YMDT);
    }
  }

  public void timeParser_YMDT(int reps) {
    for (int i = 0; i < reps; i++) {
      CookieDateParser.parseCookieDate(COOKIE_DATE_YMDT);
    }
  }
}
