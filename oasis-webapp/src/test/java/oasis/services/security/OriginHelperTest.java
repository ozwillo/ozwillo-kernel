/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.services.security;

import static org.junit.Assert.*;

import org.junit.Test;

public class OriginHelperTest {
  @Test
  public void testDefaultPort() {
    assertEquals("The origin calculated from a URI without port should NOT contains the default port related to the scheme.",
        "https://www.example.net", OriginHelper.originFromUri("https://www.example.net:443")
    );
  }

  @Test
  public void testInternationalDomainName() {
    assertEquals("The origin calculated from a URI in unicode should be encoded in punycode.",
        "https://xn--e1afmkfd.bg", OriginHelper.originFromUri("https://пример.bg")
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
