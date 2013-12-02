package oasis.web.authn;

import java.security.Principal;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;

public class OAuthPrincipal implements Principal {
  private final Account account;
  private final AccessToken accessToken;

  public OAuthPrincipal(Account account, AccessToken accessToken) {
    this.account = account;
    this.accessToken = accessToken;
  }

  @Override
  public String getName() {
    return account.getId();
  }

  public Account getAccount() {
    // TODO: load account lazily
    return account;
  }

  public AccessToken getAccessToken() {
    return accessToken;
  }
}
