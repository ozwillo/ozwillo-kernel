package oasis.web.authn;

import java.security.Principal;

import oasis.model.authn.SidToken;

public class UserSessionPrincipal implements Principal {
  private final SidToken sidToken;

  public UserSessionPrincipal(SidToken sidToken) {
    this.sidToken = sidToken;
  }

  @Override
  public String getName() {
    return sidToken.getAccountId();
  }

  public SidToken getSidToken() {
    return sidToken;
  }
}
