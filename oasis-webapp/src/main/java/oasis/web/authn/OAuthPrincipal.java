package oasis.web.authn;

import java.security.Principal;

import oasis.model.accounts.AccessToken;

public class OAuthPrincipal implements Principal {
  private final AccessToken accessToken;

  public OAuthPrincipal(AccessToken accessToken) {
    this.accessToken = accessToken;
  }

  @Override
  public String getName() {
    return accessToken.getId();
  }

  public AccessToken getAccessToken() {
    return accessToken;
  }
}
