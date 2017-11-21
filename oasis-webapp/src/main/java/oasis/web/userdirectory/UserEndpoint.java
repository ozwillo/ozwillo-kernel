/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.userdirectory;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.utils.ResponseFactory;

@Path("/d/user/{user_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class UserEndpoint {
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("user_id") String user_id;

  @GET
  public Response get() {
    UserAccount account = accountRepository.getUserAccountById(user_id);
    if (account == null) {
      return ResponseFactory.NOT_FOUND;
    }
    // TODO: implement sharing rights to filter properties depending on requesting user
    // FIXME: temporarily give full-access to the portal only; sharing only the name and nickname to other applications.
    UserAccount filteredAccount;
    if (ClientIds.PORTAL.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getServiceProviderId())) {
      filteredAccount = new PortalUserAccount(account);
    } else {
      filteredAccount = new UserAccount();
      filteredAccount.setId(account.getId());
      filteredAccount.setName(account.getName());
      filteredAccount.setNickname(account.getNickname());
    }
    return Response.ok()
        .tag(etagService.getEtag(account))
        .entity(filteredAccount)
        .build();
  }

  @PUT
  @WithScopes(Scopes.PORTAL)
  public Response replace(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      UserAccount account
  ) {
    if (!user_id.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (account.getId() != null && !account.getId().equals(user_id)) {
      ResponseFactory.unprocessableEntity("id doesn't match URL");
    }
    account.setId(user_id);

    if (ifMatch == null || ifMatch.isEmpty()) {
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

  static class PortalUserAccount extends UserAccount {
    @JsonProperty final List<String> authentication_methods;

    PortalUserAccount(UserAccount account) {
      super(account);
      setId(account.getId());
      authentication_methods = (getFranceconnect_sub() == null)
          ? ImmutableList.of("pwd")
          : ImmutableList.of("pwd", "franceconnect");
    }
  }
}
