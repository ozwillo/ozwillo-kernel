package oasis.services.security;

import static org.junit.Assert.*;

import org.junit.Test;

public class OriginHelperTest {
  @Test
  public void testDefaultPort() {
    assertEquals("The origin calculated from a URI without port should contains the default port related to the scheme.",
        "https://www.example.net:443", OriginHelper.originFromUri("https://www.example.net")
    );
  }

  @Test
  public void testInternationalDomainName() {
    assertEquals("The origin calculated from a URI in unicode should be encoded in punycode.",
        "https://xn--e1afmkfd.bg:443", OriginHelper.originFromUri("https://пример.bg:443")
    );
  }

  @Test
  public void testNoScheme() {
    assertEquals("The origin calculated from a URI without scheme should be \"" + OriginHelper.NULL_ORIGIN + "\"",
        OriginHelper.NULL_ORIGIN, OriginHelper.originFromUri("www.example.net"));
  }

  @Test
  public void testNoHost() {
    assertEquals("The origin calculated from a URI without host should be \"" + OriginHelper.NULL_ORIGIN + "\"",
        OriginHelper.NULL_ORIGIN, OriginHelper.originFromUri("http://?q=pouettagada"));
  }
}
