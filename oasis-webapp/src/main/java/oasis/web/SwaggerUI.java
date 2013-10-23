package oasis.web;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import oasis.web.view.View;

@Path("/")
public class SwaggerUI {

  @GET
  @Path("/swagger-ui.html")
  @Produces(MediaType.TEXT_HTML)
  public Response get(@Context UriInfo uriInfo) {
    Map<String, String> model = ImmutableMap.of(
            "basePath", uriInfo.getBaseUriBuilder().path("/swagger-ui/").build().toString(),
            "apiPath", uriInfo.getBaseUriBuilder().path(ApiListingResourceJSON.class).build().toString()
    );
    return Response.ok(new View(SwaggerUI.class, "SwaggerUI.get.html", model)).build();
  }

  @GET
  @Path("/swagger-ui/{resource: .+\\.js$}")
  @Produces("application/javascript")
  public Response js(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/swagger-ui/{resource: .+\\.css$}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/swagger-ui/{resource: .+\\.png$}")
  @Produces("image/png")
  public Response png(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/swagger-ui/{resource: .+\\.gif$}")
  @Produces("image/gif")
  public Response gif(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  private Response getResource(String resourceName) throws IOException {
    final URL resource;
    try {
      resource = Resources.getResource("swagger-ui/" + resourceName);
    } catch (IllegalArgumentException iae) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    URLConnection conn = resource.openConnection();
    Response.ResponseBuilder response = Response.ok()
        .entity(conn.getInputStream());

    long lastModified = conn.getLastModified();
    if (lastModified != 0) {
      response.lastModified(new Date(lastModified));
    }

    return response.build();
  }
}
