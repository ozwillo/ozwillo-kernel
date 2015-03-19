package oasis.model.authn;

import java.net.URI;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountActivationToken extends Token {
  @JsonProperty @Nullable
  private URI continueUrl;

  @Nullable
  public URI getContinueUrl() {
    return continueUrl;
  }

  public void setContinueUrl(@Nullable URI continueUrl) {
    this.continueUrl = continueUrl;
  }
}
