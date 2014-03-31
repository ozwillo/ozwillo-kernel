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
