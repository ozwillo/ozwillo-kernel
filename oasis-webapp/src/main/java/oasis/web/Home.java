package oasis.web;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import oasis.web.view.View;

@Path("/")
public class Home {

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get() {
    return Response.ok(new View(Home.class, "Home.get.html")).build();
  }

  @GET
  @Path("/favicon.ico")
  public Response favicon() {
    // TODO: we need a favicon!
    return Response.status(Response.Status.NOT_FOUND).build();
  }
}
