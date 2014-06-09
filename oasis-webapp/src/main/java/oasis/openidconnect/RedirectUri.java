package oasis.openidconnect;

import java.net.URI;
import java.net.URISyntaxException;

import com.google.common.base.Strings;

public class RedirectUri {
  /**
   * Checks that the {@code redirect_uri} is valid.
   *
   * <p>From <a href="http://tools.ietf.org/html/rfc6749#section-3.1.2">OAuth 2.0</a>:
   * <blockquote>
   * The redirection endpoint URI MUST be an absolute URI as defined by
   * [RFC3986] Section 4.3. […] The endpoint URI MUST NOT include a
   * fragment component.
   * </blockquote>
   * <p>In addition, the {@code redirect_uri} MUST also use an {@code http} or {@code https} scheme.
   */
  public static boolean isValid(String redirect_uri) {
    final URI ruri;
    try {
      ruri = new URI(redirect_uri);
    } catch (URISyntaxException use) {
      return false;
    }

    if (!ruri.isAbsolute() || ruri.isOpaque() || !Strings.isNullOrEmpty(ruri.getRawFragment())) {
      return false;
    }

    if (!"http".equals(ruri.getScheme()) && !"https".equals(ruri.getScheme())) {
      return false;
    }

    return true;
  }
}
