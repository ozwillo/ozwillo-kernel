package oasis.model.accounts;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AccessToken extends Token {
  @JsonProperty
  @ApiModelProperty
  private String serviceProviderId;

  @JsonProperty
  @ApiModelProperty
  private Set<String> scopeIds;

  @JsonProperty
  @ApiModelProperty
  private String refreshTokenId;

  public String getRefreshTokenId() {
    return refreshTokenId;
  }

  public void setRefreshTokenId(String refreshTokenId) {
    this.refreshTokenId = refreshTokenId;
  }

  public String getServiceProviderId() {
    return serviceProviderId;
  }

  public void setServiceProviderId(String serviceProviderId) {
    this.serviceProviderId = serviceProviderId;
  }

  public Set<String> getScopeIds() {
    return scopeIds;
  }

  public void setScopeIds(Set<String> scopeIds) {
    this.scopeIds = scopeIds;
  }
}
