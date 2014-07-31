package oasis.web.userdirectory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/d/user/{user_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "user", description = "User profile")
public class UserEndpoint {
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("user_id") String user_id;

  @GET
  @ApiOperation(
      value = "Retrieves information about a user",
      response = UserAccount.class
  )
  public Response get() {
    UserAccount account = accountRepository.getUserAccountById(user_id);
    if (account == null) {
      return ResponseFactory.NOT_FOUND;
    }
    // TODO: implement sharing rights to filter properties depending on requesting user
    return Response.ok()
        .tag(etagService.getEtag(account))
        .entity(account)
        .build();
  }

  @PUT
  @ApiOperation(
      value = "Updates the user's profile",
      response = UserAccount.class
  )
  public Response replace(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
      UserAccount account
  ) {
    if (!user_id.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (account.getId() != null && !account.getId().equals(user_id)) {
      ResponseFactory.unprocessableEntity("id doesn't match URL");
    }
    account.setId(user_id);

    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    if (Strings.isNullOrEmpty(account.getPhone_number()) || !Boolean.TRUE.equals(account.getPhone_number_verified())) {
      // Don't store phone_number_verified if not needed (no phone_number) or not true
      account.setPhone_number_verified(null);
    }

    try {
      account = accountRepository.updateAccount(account, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (account == null) {
      return ResponseFactory.NOT_FOUND;
    }

    // Always send phone_verified (true or false) when there's a phone, never send it (null) otherwise.
    if (account.getPhone_number() == null) {
      account.setPhone_number_verified(null);
    } else {
      account.setPhone_number_verified(Boolean.TRUE.equals(account.getPhone_number_verified()));
    }

    return Response.ok()
        .tag(etagService.getEtag(account))
        .entity(account)
        .build();
  }
}