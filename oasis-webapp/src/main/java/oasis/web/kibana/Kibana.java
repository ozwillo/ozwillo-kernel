package oasis.web.kibana;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import oasis.web.view.View;

@Path("/")
public class Kibana {

  @GET
  @Path("/kibana/")
  @Produces("text/html")
  public Response get() throws IOException {
    return getResource("index.html");
  }

  @GET
  @Path("/kibana/{resource: .+\\.html}")
  @Produces("text/html")
  public Response html(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/config.js")
  @Produces("application/javascript")
  public Response config() throws IOException {
    Map<String, String> model = ImmutableMap.of(
        "esPath", "es"
    );
    return Response.ok(new View(Kibana.class, "Kibana.config.js", model)).build();
  }

  @GET
  @Path("/kibana/{resource: .+\\.js$}")
  @Produces("application/javascript")
  public Response js(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.json$}")
  @Produces("application/json")
  public Response json(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.css$}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.png$}")
  @Produces("image/png")
  public Response png(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.gif$}")
  @Produces("image/gif")
  public Response gif(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.otf}")
  @Produces("application/x-font-opentype")
  public Response otf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.ttf}")
  @Produces("application/x-font-ttf")
  public Response ttf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("/kibana/{resource: .+\\.woff}")
  @Produces("application/font-woff")
  public Response woff(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  private Response getResource(String resourceName) throws IOException {
    final URL resource;
    try {
      resource = Resources.getResource("kibana/" + resourceName);
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
