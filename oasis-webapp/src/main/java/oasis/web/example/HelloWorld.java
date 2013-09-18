package oasis.web.example;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import oasis.model.example.Greeting;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
@Api(value="/hello", description = "Hello world example")
public class HelloWorld {
  @GET
  @ApiOperation(value = "Test GET method", notes = "Returns a Greeting object containing the 'param' parameter", response = oasis.model.example.Greeting.class)
  @ApiResponses(value = { @ApiResponse(code = 400, message = "Invalid parameter supplied") })
  public Response getGreeting(@QueryParam("param") String msg) {
    if (msg == null || msg.isEmpty()){
      return Response.status(Response.Status.BAD_REQUEST).entity("Missing parameter").build();
    }
    Greeting result = new Greeting();
    result.setName(msg);
    return Response.status(200).entity(result).build();

  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Test POST method", notes = "Accepts a Greeting object", response = oasis.model.example.Greeting.class)
  public Response postGreeting(Greeting g){
    return Response.status(200).build();
  }
}
