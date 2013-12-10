package oasis.web.authn;

import java.security.Principal;

public class AccountPrincipal implements Principal {
  private final String accountId;

  public AccountPrincipal(String accountId) {
    this.accountId = accountId;
  }

  @Override
  public String getName() {
    return accountId;
  }

  public String getAccountId() {
    return accountId;
  }
}
