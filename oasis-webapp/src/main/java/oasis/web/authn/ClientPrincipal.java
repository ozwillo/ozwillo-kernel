package oasis.web.authn;

import java.security.Principal;

public class ClientPrincipal implements Principal {
  private final String clientId;

  public ClientPrincipal(String clientId) {
    this.clientId = clientId;
  }

  @Override
  public String getName() {
    return clientId;
  }

  public String getClientId() {
    return clientId;
  }
}
