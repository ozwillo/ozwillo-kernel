package oasis.web;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.common.io.Resources;
import com.google.template.soy.data.SoyMapData;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;

import oasis.soy.SoyTemplate;
import oasis.soy.templates.SwaggerUISoyInfo;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/")
public class SwaggerUI {

  @GET
  @Path("/swagger-ui.html")
  public Response redirectToNewUri(@Context UriInfo uriInfo) {
    return Response
        .status(Response.Status.MOVED_PERMANENTLY)
        .location(Resteasy1099.getBaseUriBuilder(uriInfo).path("/swagger-ui/").build())
        .build();
  }

  @GET
  @Path("/swagger-ui/")
  public Response getDirectory(@Context UriInfo uriInfo) {
    if (uriInfo.getPath().endsWith("/")) {
      return get(uriInfo);
    } else {
      return redirectToNewUri(uriInfo);
    }
  }

  @GET
  @Path("/swagger-ui/index.html")
  @Produces(MediaType.TEXT_HTML)
  public Response get(@Context UriInfo uriInfo) {
    SoyMapData model = new SoyMapData(
        SwaggerUISoyInfo.Param.BASE_PATH, Resteasy1099.getBaseUriBuilder(uriInfo).path("/swagger-ui/").build().toString(),
        SwaggerUISoyInfo.Param.API_PATH, Resteasy1099.getBaseUriBuilder(uriInfo).path(ApiListingResourceJSON.class).build().toString()
    );
    return Response.ok(new SoyTemplate(SwaggerUISoyInfo.SWAGGER_UI, model)).build();
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
      resource = Resources.getResource("META-INF/resources/webjars/swagger-ui/2.0.17/" + resourceName);
    } catch (IllegalArgumentException iae) {
      return ResponseFactory.NOT_FOUND;
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
