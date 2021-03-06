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
package oasis.web.utils;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class UserAgentFingerprinter {

  // Let's use the fastest algorithm to start with. Maybe switch to MurMur3 later for better uniqueness.
  private static final HashFunction HASH_FUNCTION = Hashing.adler32();

  private static final String[] FINGERPRINTED_HEADERS = {
      HttpHeaders.USER_AGENT,
      HttpHeaders.ACCEPT_CHARSET,
      HttpHeaders.ACCEPT_ENCODING,
      HttpHeaders.ACCEPT_LANGUAGE,
  };

  public byte[] fingerprint(ContainerRequestContext ctx) {
    Hasher hasher = HASH_FUNCTION.newHasher();
    for (String name : FINGERPRINTED_HEADERS) {
      putHeader(hasher, name, ctx.getHeaderString(name));
    }
    return hasher.hash().asBytes();
  }

  public byte[] fingerprint(HttpHeaders ctx) {
    Hasher hasher = HASH_FUNCTION.newHasher();
    for (String name : FINGERPRINTED_HEADERS) {
      putHeader(hasher, name, ctx.getHeaderString(name));
    }
    return hasher.hash().asBytes();
  }

  private void putHeader(Hasher hasher, String name, String value) {
    if (value != null) {
      if (HttpHeaders.ACCEPT_ENCODING.equals(name)) {
        // special-case for Chrome, which sends an "sdch" accept-encoding randomly [1],
        // but never sends it on POST requests [2] (e.g. when submitting the login form
        // and creating a SidToken). The Accept-Encoding value is hard-coded [3,4] so we
        // simply strip ",sdch" or ", sdch" before hashing the value (no need for
        // complicated algorithms like splitting on commas before reassembling the header
        // value).
        //
        // [1] https://chromium.googlesource.com/chromium/src/+/37.0.2062.94/net/url_request/url_request_http_job.cc#487
        // [2] https://chromium.googlesource.com/chromium/src/+/37.0.2062.94/net/url_request/url_request_http_job.cc#470
        // [3] https://chromium.googlesource.com/chromium/src/+/37.0.2062.94/net/url_request/url_request_http_job.cc#512
        // [4] https://chromium.googlesource.com/chromium/src/+/39.0.2171.65/net/url_request/url_request_http_job.cc#528
        value = value.replace(",sdch", "").replace(", sdch", "");
      }
      hasher.putString(value, StandardCharsets.UTF_8);
    }
  }
}
