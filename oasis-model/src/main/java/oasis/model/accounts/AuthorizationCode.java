package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AuthorizationCode extends AbstractOAuthToken {
  @JsonProperty
  @ApiModelProperty
  private String nonce;

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
}
