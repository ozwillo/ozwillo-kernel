package oasis.web.authn;

import java.net.URI;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import oasis.services.cookies.CookieFactory;

@Path("/a/logout")
public class Logout {
  @Context SecurityContext securityContext;
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
        .cookie(CookieFactory.createExpiredCookie(UserAuthenticationFilter.COOKIE_NAME, securityContext.isSecure()))
        .build();
  }
}
