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
        uriInfo.getBaseUri().toASCIIString(), // overwrite basePath
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
}
