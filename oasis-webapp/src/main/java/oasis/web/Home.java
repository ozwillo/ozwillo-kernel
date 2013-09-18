package oasis.web;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

@Path("/")
public class Home {

  @GET
  @Produces(MediaType.TEXT_HTML)
  public String get(@Context UriInfo uriInfo) {
    return "<html>"
        + "<body>"
        + "<h1>It works</h1>"
        + "</body>"
        + "</html>";
  }

}
