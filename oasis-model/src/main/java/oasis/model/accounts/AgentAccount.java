package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class AgentAccount extends UserAccount {

  @JsonProperty
  @ApiModelProperty(required = true)
  private String organizationId;

  @JsonProperty
  @ApiModelProperty(required = true)
  private boolean admin;

  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }
}
