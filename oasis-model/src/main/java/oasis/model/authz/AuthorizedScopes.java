package oasis.model.authz;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("authorizedScopes")
public class AuthorizedScopes {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String serviceProviderId;

  @JsonProperty
  @ApiModelProperty(required = true)
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
