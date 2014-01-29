package oasis.services.security;

import java.util.UUID;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;

public class OriginHelper {
  public static final String NULL_ORIGIN = "null";
  /**
   * Compute the origin of a URI
   *
   * @see <a href="http://tools.ietf.org/search/rfc6454#section-4">RFC 6454</a></a>
   */
  public static String originFromUri(String uri) {
    URL url;
    try {
      url = URL.parse(uri);
    } catch (GalimatiasParseException e) {
      return NULL_ORIGIN;
    }
    if (!url.isHierarchical()) {
      return UUID.randomUUID().toString();
    }
    return serializeOrigin(url.scheme(), url.host().toString(), url.port());
  }

  private static String serializeOrigin(String uriScheme, String uriHost, int uriPort) {
    return uriScheme + "://" + uriHost + ":" + uriPort;
  }
}
