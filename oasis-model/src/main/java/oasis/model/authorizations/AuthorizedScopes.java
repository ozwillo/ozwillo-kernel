package oasis.model.authorizations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("authorizedScopes")
public class AuthorizedScopes {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String accountId;
  @JsonProperty
  @ApiModelProperty(required = true)
  private String serviceProviderId;
  @JsonProperty
  @ApiModelProperty(required = true)
  private String[] scopeIds;

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getServiceProviderId() {
    return serviceProviderId;
  }

  public void setServiceProviderId(String serviceProviderId) {
    this.serviceProviderId = serviceProviderId;
  }

  public String[] getScopeIds() {
    return scopeIds;
  }

  public void setScopeIds(String[] scopeIds) {
    this.scopeIds = scopeIds;
  }
}
