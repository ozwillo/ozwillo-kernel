package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public class UserAccount extends Account {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String emailAddress;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String identityId;

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getIdentityId() {
    return identityId;
  }

  public void setIdentityId(String identityId) {
    this.identityId = identityId;
  }
}
