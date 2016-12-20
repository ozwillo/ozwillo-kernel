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

import java.util.UUID;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;

public class OriginHelper {
  public static final String NULL_ORIGIN = "null";

  /**
   * Compute the origin of a URI
   *
   * @see <a href="http://tools.ietf.org/search/rfc6454#section-4">RFC 6454 (Origin of a URI)</a>
   * @see <a href="https://tools.ietf.org/html/rfc6454#section-6.2">RFC 6454 (ASCII Serialization of an Origin)</a>
   * @see <a href="https://tools.ietf.org/html/rfc6454#section-7.3">RFC 6454 (The HTTP Origin Header Field – User Agent Requirements)</a>
   */
  public static String originFromUri(String uri) {
    URL url;
    try {
      url = URL.parse(uri);
    } catch (GalimatiasParseException e) {
      return NULL_ORIGIN;
    }
    if (url.isOpaque() || url.host() == null) {
      return UUID.randomUUID().toString();
    }
    return serializeOrigin(url.scheme(), url.host().toString(), url.port(), url.defaultPort());
  }

  private static String serializeOrigin(String uriScheme, String uriHost, int uriPort, int defaultPort) {
    return uriScheme + "://" + uriHost + (uriPort != defaultPort ? ":" + uriPort : "");
  }
}
