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

import oasis.soy.SoyTemplate;
import oasis.soy.templates.HomeSoyInfo;
import oasis.web.utils.ResponseFactory;

@Path("/")
public class StaticResources {

  @GET
  @Path("")
  @Produces(MediaType.TEXT_HTML)
  public Response home() {
    return Response.ok(new SoyTemplate(HomeSoyInfo.HOME)).build();
  }

  @GET
  @Path("/favicon.ico")
  @Produces("image/vnd.microsoft.icon")
  public Response favicon() throws IOException {
    return getResource("favicon.ico");
  }

  @GET
  @Path("{resource: .+\\.css}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.jpg}")
  @Produces("image/jpg")
  public Response jpg(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.png}")
  @Produces("image/png")
  public Response png(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.svg}")
  @Produces("image/svg+xml")
  public Response svg(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.woff}")
  @Produces("application/font-woff")
  public Response woff(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.ttf}")
  @Produces("application/x-font-ttf")
  public Response ttf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.eot}")
  @Produces("application/vnd.ms-fontobject")
  public Response eot(@PathParam("resource") String resourceName) throws IOException {
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
