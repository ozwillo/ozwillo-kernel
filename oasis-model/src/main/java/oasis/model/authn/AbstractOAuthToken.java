package oasis.model.authn;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public abstract class AbstractOAuthToken extends Token {
  @JsonProperty
  @ApiModelProperty
  private String serviceProviderId;

  @JsonProperty
  @ApiModelProperty
  private Set<String> scopeIds;

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
