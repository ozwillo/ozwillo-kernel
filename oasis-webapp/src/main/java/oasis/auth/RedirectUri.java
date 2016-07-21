/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
package oasis.auth;

import static com.google.common.base.Preconditions.*;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

public class RedirectUri {

  private static final Escaper PARAMETER_ESCAPER = UrlEscapers.urlFormParameterEscaper();

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

  private final StringBuilder sb;
  private char separator;
  private boolean initialized;

  public RedirectUri(String redirect_uri) {
    assert isValid(redirect_uri);
    this.sb = new StringBuilder(redirect_uri);

    separator = (redirect_uri.indexOf('?') < 0)  ? '?' : '&';
  }

  public RedirectUri setState(@Nullable String state) {
    // XXX: check that it's only set once?
    appendQueryParam("state", state);
    return this;
  }

  // TODO: add error_uri?
  public RedirectUri setError(String error, @Nullable String description) {
    checkState(!initialized);
    initialized = true;
    appendQueryParam("error", error);
    appendQueryParam("error_description", description);
    return this;
  }

  public RedirectUri setCode(String code) {
    checkState(!initialized);
    initialized = true;
    appendQueryParam("code", code);
    return this;
  }

  public RedirectUri setSessionState(String state) {
    // XXX: check that it's only set once?
    appendQueryParam("session_state", state);
    return this;
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  private void appendQueryParam(String paramName, @Nullable String paramValue) {
    if (paramValue == null) {
      return;
    }
    assert PARAMETER_ESCAPER.escape(paramName).equals(paramName) : "paramName needs escaping!";
    sb.append(separator).append(paramName).append('=').append(PARAMETER_ESCAPER.escape(paramValue));
    separator = '&';
  }
}
