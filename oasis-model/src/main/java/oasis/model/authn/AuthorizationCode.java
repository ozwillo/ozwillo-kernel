package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AuthorizationCode extends AccessTokenGenerator {
  @JsonProperty
  @ApiModelProperty
  private String nonce;

  @JsonProperty
  @ApiModelProperty
  private String redirectUri;

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
}
