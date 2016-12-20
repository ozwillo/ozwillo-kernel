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
package oasis.web.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import com.google.common.net.UrlEscapers;

/**
 * "Lenient" parser for URIs.
 * <p>
 * We need/want a lenient parser that accepts unencoded spaces (among others). We also need to make sure encoded
 * sequences will be properly encoded when later decoding the URI. A value should correctly round-trip when passed
 * as a query parameter between resources (e.g. using redirects).
 */
@Provider
public class UriParamConverterProvider implements ParamConverterProvider {
  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (URI.class.equals(rawType)) {
      return (ParamConverter<T>) new UriParamConverter();
    }
    return null;
  }

  private static class UriParamConverter implements ParamConverter<URI> {
    @Override
    public URI fromString(String value) {
      // escape raw '{' and '}' so they're not parsed as URITemplate parameters
      value = value.replace("{", "%7B").replace("}", "%7D");
      return UriBuilder.fromUri(value).build();
    }

    @Override
    public String toString(URI value) {
      return UrlEscapers.urlFormParameterEscaper().escape(value.toASCIIString());
    }
  }
}
