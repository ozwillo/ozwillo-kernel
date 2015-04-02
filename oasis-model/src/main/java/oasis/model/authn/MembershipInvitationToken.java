package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MembershipInvitationToken extends Token {
  @JsonProperty
  private String organizationMembershipId;

  public String getOrganizationMembershipId() {
    return organizationMembershipId;
  }

  public void setOrganizationMembershipId(String organizationMembershipId) {
    this.organizationMembershipId = organizationMembershipId;
  }
}
