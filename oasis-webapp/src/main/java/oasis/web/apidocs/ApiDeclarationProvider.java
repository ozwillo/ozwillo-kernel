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
package oasis.web.apidocs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import com.wordnik.swagger.model.ApiListing;

import oasis.web.resteasy.Resteasy1099;

/**
 * Hijacks Swagger's MessageBodyWriter for ApiListing so we can dynamically provide the {@code basePath} depending on the requested URI.
 */
public class ApiDeclarationProvider extends com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider {

  @Context UriInfo uriInfo;

  @Override
  public void writeTo(ApiListing data, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException {
    // We make a shallow clone to overwrite the basePath, then hands the clone down to the super implementation.
    data = new ApiListing(
        data.apiVersion(),
        data.swaggerVersion(),
        getCanonicalBaseUri(), // overwrite basePath
        data.resourcePath(),
        data.produces(),
        data.consumes(),
        data.protocols(),
        data.authorizations(),
        data.apis(),
        data.models(),
        data.description(),
        data.position()
    );
    super.writeTo(data, type, genericType, annotations, mediaType, headers, out);
  }

  private String getCanonicalBaseUri() {
    final String baseUri = Resteasy1099.getBaseUri(uriInfo).toASCIIString();
    if (baseUri.endsWith("/")) {
      return baseUri.substring(0, baseUri.length() - 1);
    }
    return baseUri;
  }
}
