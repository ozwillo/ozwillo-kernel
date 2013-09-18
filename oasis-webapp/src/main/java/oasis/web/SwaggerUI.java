package oasis.web;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.wordnik.swagger.config.ConfigFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;

@Path("/swagger-ui")
public class SwaggerUI {
  @Context
  private HttpServletRequest request;

  @GET
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  public Response get() {
    Map<String, String> model = ImmutableMap.of(
            "basePath", ConfigFactory.config().getBasePath(),
            "apiPath", ConfigFactory.config().getApiPath()
    );
    return Response.ok(new View("oasis/web/SwaggerUI.get.html", model)).build();
  }

  @GET
  @Path("/{resource: .+}")
  public Response getResource(@PathParam("resource") String resourceName) throws IOException, URISyntaxException {
    URL resource = Resources.getResource("swagger-ui/" + resourceName);
    URLConnection conn = resource.openConnection();
    Date lastModified = new Date(conn.getLastModified());
    String type = Files.probeContentType(Paths.get(resource.toURI()));
    return Response.ok()
            .entity(conn.getInputStream())
            .type(MediaType.valueOf(type))
            .lastModified(lastModified)
            .build();
  }
}
