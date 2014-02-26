package oasis.web.account;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.collect.ImmutableMap;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.SidToken;
import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.view.View;

@Authenticated
@User
// TODO: Limit to trusted users
@Path("/a/profile")
public class ProfilePage {
  @Context SecurityContext securityContext;
  @Inject AccountRepository accountRepository;
  @Inject IdentityRepository identityRepository;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get() {
    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    Account account = accountRepository.getAccount(sidToken.getAccountId());
    if (!(account instanceof UserAccount)) {
      return Response.serverError().build();
    }

    UserAccount userAccount = (UserAccount) account;
    Identity identity = identityRepository.getIdentity(userAccount.getIdentityId());
    if (identity == null) {
      return Response.serverError().build();
    }

    // TODO: Manage CSRF
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new View(ProfilePage.class, "Profile.html", ImmutableMap.of(
            "email", userAccount.getEmailAddress(),
            "initData", new ProfileInfo(identity)
        )))
        .build();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response put(ProfileInfo profileInfo) {
    // TODO: Validate input
    // TODO: Validate CSRf
    // TODO: Validate authenticated user and the identity received
    boolean updated = identityRepository.updateIdentity(profileInfo.toIdentity());
    if (!updated) {
      return Response.serverError().build();
    }
    return Response.ok().build();
  }
}
