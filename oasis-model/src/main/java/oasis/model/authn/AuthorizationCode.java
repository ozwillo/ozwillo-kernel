package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AuthorizationCode extends AbstractOAuthToken {
  @JsonProperty
  private String nonce;

  @JsonProperty
  private String redirectUri;

  @JsonProperty
  private boolean shouldSendAuthTime;

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public boolean shouldSendAuthTime() {
    return shouldSendAuthTime;
  }

  public void setShouldSendAuthTime(boolean shouldSendAuthTime) {
    this.shouldSendAuthTime = shouldSendAuthTime;
  }
}
