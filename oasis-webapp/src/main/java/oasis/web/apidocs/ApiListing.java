package oasis.web.apidocs;

import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import javax.servlet.ServletConfig;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/**
 * Extends Swagger's {@link ApiListingResource} to compute the {@link com.wordnik.swagger.config.SwaggerConfig#getBasePath() base path}
 * dynamically to match with the request (same origin).
 * <p>
 * <b>Implementation note:</b> the class assumes that it is the only class making use of the
 * {@link com.wordnik.swagger.jaxrs.listing.ApiListingCache ApiListingCache}. If that wouldn't be the case, all classes should be modified
 * to {@code synchronize} on the {@code ApiListingCache} (in addition to this class, here).
 */
@Path("/api-docs")
@Produces(MediaType.APPLICATION_JSON)
public class ApiListing extends ApiListingResource {

  private static String basePath;

  @Override
  public Response resourceListing(Application app, ServletConfig sc, HttpHeaders headers, UriInfo uriInfo) {
    synchronized (ApiListing.class) {
      checkBasePath(uriInfo);
      return super.resourceListing(app, sc, headers, uriInfo);
    }
  }

  @Override
  public Response apiDeclaration(String route, Application app, ServletConfig sc, HttpHeaders headers, UriInfo uriInfo) {
    synchronized (ApiListing.class) {
      checkBasePath(uriInfo);
      return super.apiDeclaration(route, app, sc, headers, uriInfo);
    }
  }

  private void checkBasePath(UriInfo uriInfo) {
    String requestBasePath = uriInfo.getBaseUri().toASCIIString();
    if (!requestBasePath.equals(basePath)) {
      basePath = requestBasePath;
      ConfigFactory.config().setBasePath(basePath);
      ConfigFactory.config().setApiPath(UriBuilder.fromResource(ApiListing.class).build().toString());
      invalidateCache();
    }
  }
}
