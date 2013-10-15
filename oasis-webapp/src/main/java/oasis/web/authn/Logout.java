package oasis.web.authn;

import java.net.URI;
import java.util.Date;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/a/logout")
public class Logout {
  @SuppressWarnings("deprecation")
  private static final Date FAR_PAST = new Date(108, 0, 20, 11, 10);

  @Context UriInfo uriInfo;

  @GET
  public Response get(@QueryParam(Login.CONTINUE_PARAM) URI continueUrl) {
    return logout(continueUrl);
  }

  @POST
  public Response post(@FormParam(Login.CONTINUE_PARAM) URI continueUrl) {
    return logout(continueUrl);
  }

  private Response logout(URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = Login.defaultContinueUrl(uriInfo);
    }
    return Response.seeOther(continueUrl)
        .cookie(Login.createCookie(null, FAR_PAST))
        .build();
  }
}
