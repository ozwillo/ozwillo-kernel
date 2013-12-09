package oasis.web.authz;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;

@Authenticated @Client
@Path("/a/tokeninfo")
@Api(value = "/a/tokeninfo", description = "Introspection Endpoint")
public class IntrospectionEndpoint {

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Get information about an access token.",
      notes = "See the <a href=\"http://tools.ietf.org/html/draft-richer-oauth-introspection\">DRAFT</a> for more information.",
      response = IntrospectionResponse.class
  )
  public Response post(@FormParam("token") String token) throws IOException {
    // TODO: Validate the token

    IntrospectionResponse introspectionResponse = new IntrospectionResponse()
        .setActive(true);

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity(introspectionResponse)
        .build();
  }
}
