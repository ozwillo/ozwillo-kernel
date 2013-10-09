package oasis.web.example;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import oasis.model.example.Greeting;

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
@Api(value="/hello", description = "Hello world example")
public class HelloWorld {
  @GET
  @ApiOperation(
      value = "Test GET method",
      notes = "Returns a Greeting object containing the 'param' parameter",
      response = oasis.model.example.Greeting.class
  )
  @ApiResponses({
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND, message = "Invalid parameter supplied")
  })
  public Response getGreeting(@QueryParam("param") String msg) {
    if (msg == null || msg.isEmpty()){
      return Response.status(Response.Status.NOT_FOUND).entity("Missing parameter").build();
    }
    Greeting result = new Greeting();
    result.setName(msg);
    return Response.ok(result).build();

  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Test POST method",
      notes = "Accepts a Greeting object",
      response = oasis.model.example.Greeting.class
  )
  public Response postGreeting(Greeting g){
    return Response.ok().build();
  }
}
