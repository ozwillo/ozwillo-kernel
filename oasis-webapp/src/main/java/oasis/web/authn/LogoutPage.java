package oasis.web.authn;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.cookies.CookieFactory;

@Path("/a/logout")
public class LogoutPage {
  private static final Logger logger = LoggerFactory.getLogger(LogoutPage.class);

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;

  @GET
  public Response get(@QueryParam(LoginPage.CONTINUE_PARAM) URI continueUrl,
      @CookieParam(UserAuthenticationFilter.COOKIE_NAME) String sidCookie) {
    return logout(continueUrl, sidCookie);
  }

  @POST
  public Response post(@FormParam(LoginPage.CONTINUE_PARAM) URI continueUrl,
      @CookieParam(UserAuthenticationFilter.COOKIE_NAME) String sidCookie) {
    return logout(continueUrl, sidCookie);
  }

  private Response logout(URI continueUrl, String serializedSidToken) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(uriInfo);
    }

    SidToken sidToken = tokenHandler.getCheckedToken(serializedSidToken, SidToken.class);
    if (sidToken != null) {
      boolean tokenRevoked = tokenRepository.revokeToken(sidToken.getId());
      if (!tokenRevoked) {
        logger.error("No SidToken was found when trying to revoke it.");
      }
    }

    return Response.seeOther(continueUrl)
        .cookie(CookieFactory.createExpiredCookie(UserAuthenticationFilter.COOKIE_NAME, securityContext.isSecure()))
        .build();
  }
}
