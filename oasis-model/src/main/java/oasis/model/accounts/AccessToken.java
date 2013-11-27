package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessToken extends Token {
  @JsonProperty
  private String refreshTokenId;

  public String getRefreshTokenId() {
    return refreshTokenId;
  }

  public void setRefreshTokenId(String refreshTokenId) {
    this.refreshTokenId = refreshTokenId;
  }
}
