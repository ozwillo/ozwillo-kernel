package oasis.web.authz;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Token;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.authn.TokenRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;

@Authenticated @Client
@Path("/a/tokeninfo")
@Api(value = "/a/tokeninfo", description = "Introspection Endpoint")
public class IntrospectionEndpoint {
  private static final Joiner SCOPE_JOINER = Joiner.on(' ').skipNulls();

  @Context SecurityContext securityContext;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AccountRepository accountRepository;
  @Inject ApplicationRepository applicationRepository;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Get information about an access token.",
      notes = "See the <a href=\"http://tools.ietf.org/html/draft-richer-oauth-introspection\">DRAFT</a> for more information.",
      response = IntrospectionResponse.class
  )
  public Response post(@FormParam("token") String token) throws IOException {
    DataProvider dataProvider = applicationRepository.getDataProvider(((ClientPrincipal) securityContext.getUserPrincipal()).getClientId());
    if (dataProvider == null) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .build();
    }
    if (Strings.isNullOrEmpty(token)) {
      return error();
    }

    Token unserializedToken = TokenSerializer.unserialize(token);
    if (unserializedToken == null) {
      return error();
    }

    // Preventive check in order to not make a call to the database
    if (!tokenHandler.checkTokenValidity(unserializedToken)) {
      return error();
    }

    Token unverifiedToken = tokenRepository.getToken(unserializedToken.getId());
    if (!(unverifiedToken instanceof AccessToken)) {
      return error();
    }
    // True check not based on the token given by the untrusted client
    if (!tokenHandler.checkTokenValidity(unverifiedToken)) {
      return error();
    }

    AccessToken accessToken = (AccessToken) unverifiedToken;

    // XXX: load an entire account just for an account id ?
    Account account = accountRepository.getAccountByTokenId(accessToken.getId());

    IntrospectionResponse introspectionResponse;
    long issuedAtTime = accessToken.getCreationTime().getMillis();
    long expireAt = issuedAtTime + accessToken.getTimeToLive().getMillis();

    Set<String> scopeIds = Sets.newHashSet(accessToken.getScopeIds());
    // Remove all scopes which don't belong to the data provider
    scopeIds.retainAll(dataProvider.getScopeIds());

    if (scopeIds.isEmpty()) {
      return error();
    }

    introspectionResponse = new IntrospectionResponse()
        .setActive(true)
        .setExp(TimeUnit.MILLISECONDS.toSeconds(expireAt))
        .setIat(TimeUnit.MILLISECONDS.toSeconds(issuedAtTime))
        .setScope(SCOPE_JOINER.join(scopeIds))
        .setClient_id(accessToken.getServiceProviderId())
        .setSub(account.getId())
        .setToken_type("Bearer");

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity(introspectionResponse)
        .build();
  }

  private Response error() {
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity(new IntrospectionResponse().setActive(false))
        .build();
  }
}
