package oasis.model.authn;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public abstract class AbstractOAuthToken extends AbstractAccountToken {
  @JsonProperty
  @ApiModelProperty
  private String serviceProviderId;

  @JsonProperty
  @ApiModelProperty
  private Set<String> scopeIds = new HashSet<>();

  public String getServiceProviderId() {
    return serviceProviderId;
  }

  public void setServiceProviderId(String serviceProviderId) {
    this.serviceProviderId = serviceProviderId;
  }

  public Set<String> getScopeIds() {
    return Collections.unmodifiableSet(scopeIds);
  }

  public void setScopeIds(Set<String> scopeIds) {
    this.scopeIds = new HashSet<>(scopeIds);
  }
}
