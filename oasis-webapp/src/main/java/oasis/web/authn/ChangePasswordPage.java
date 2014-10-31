package oasis.web.authn;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import com.google.template.soy.data.SoyMapData;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.TokenRepository;
import oasis.auth.AuthModule;
import oasis.services.authn.CredentialsService;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.ChangePasswordSoyInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.ChangePasswordSoyTemplateInfo;
import oasis.soy.templates.ChangePasswordSoyInfo.PasswordChangedSoyTemplateInfo;

@Path("/a/password")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class ChangePasswordPage {
  @Inject AccountRepository accountRepository;
  @Inject CredentialsService credentialsService;
  @Inject TokenRepository tokenRepository;
  @Inject AuthModule.Settings settings;

  @Context SecurityContext securityContext;

  @GET
  public Response get() {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    return form(Response.ok(), account, null);
  }

  @POST
  public Response post(
      @FormParam("oldpwd") String oldpwd,
      @FormParam("newpwd") String newpwd,
      @FormParam("confirmpwd") String confirmpwd
  ) {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount account = accountRepository.getUserAccountById(userId);
    if (!credentialsService.checkPassword(ClientType.USER, userId, oldpwd)) {
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordChangeError.BAD_PASSWORD);
    }

    credentialsService.setPassword(ClientType.USER, userId, newpwd);

    // revoke all sessions / tokens
    tokenRepository.revokeTokensForAccount(userId);

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure()))
        .entity(new SoyTemplate(ChangePasswordSoyInfo.PASSWORD_CHANGED,
            account.getLocale(),
            new SoyMapData(
                PasswordChangedSoyTemplateInfo.CONTINUE, settings.landingPage == null ? null : settings.landingPage.toString()
            )))
        .build();
  }

  private Response form(Response.ResponseBuilder builder, UserAccount account, @Nullable PasswordChangeError error) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(ChangePasswordSoyInfo.CHANGE_PASSWORD,
            account.getLocale(),
            new SoyMapData(
                ChangePasswordSoyTemplateInfo.EMAIL, account.getEmail_address(),
                ChangePasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(ChangePasswordPage.class).build().toString(),
                // FIXME: get the URL to the profile page
                ChangePasswordSoyTemplateInfo.PORTAL_URL, settings.landingPage == null ? null : settings.landingPage.toString(),
                ChangePasswordSoyTemplateInfo.ERROR, error == null ? null : error.name()
            )
        ))
        .build();
  }

  enum PasswordChangeError {
    BAD_PASSWORD
  }
}
