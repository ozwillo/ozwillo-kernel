package oasis.web;

import com.google.common.collect.ImmutableMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Date;

@Path("/")
public class Home {

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(@Context UriInfo uriInfo) {
    return Response.ok(new View("oasis/web/Home.get.html", ImmutableMap.of("date", new Date()))).build();
  }

  @GET
  @Path("/favicon.ico")
  public Response favicon() {
    // TODO: we need a favicon!
    return Response.status(Response.Status.NOT_FOUND).build();
  }
}
