package oasis.web;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.io.Resources;

import oasis.web.utils.ResponseFactory;
import oasis.web.view.View;

@Path("/")
public class StaticResources {

  @GET
  @Path("")
  @Produces(MediaType.TEXT_HTML)
  public Response home() {
    return Response.ok(new View(StaticResources.class, "Home.html")).build();
  }

  @GET
  @Path("/favicon.ico")
  public Response favicon() {
    // TODO: we need a favicon!
    return ResponseFactory.NOT_FOUND;
  }

  @GET
  @Path("{resource: .+\\.css}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.js}")
  @Produces("application/javascript")
  public Response js(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  private Response getResource(String resourceName) throws IOException {
    final URL resource;
    try {
      resource = Resources.getResource("oasis-ui/" + resourceName);
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
