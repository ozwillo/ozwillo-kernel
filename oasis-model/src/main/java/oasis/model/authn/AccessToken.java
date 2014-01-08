package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AccessToken extends AbstractOAuthToken {
  @JsonProperty
  @ApiModelProperty
  private String refreshTokenId;

  public String getRefreshTokenId() {
    return refreshTokenId;
  }

  public void setRefreshTokenId(String refreshTokenId) {
    this.refreshTokenId = refreshTokenId;
  }
}
