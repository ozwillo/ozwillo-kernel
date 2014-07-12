package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

public abstract class UserAccount extends Account {
  @JsonProperty
  @ApiModelProperty(required = true)
  private String emailAddress;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String identityId;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String password;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String passwordSalt;

  @JsonProperty
  @ApiModelProperty
  private String picture;

  @JsonProperty
  @ApiModelProperty
  private String zoneInfo;

  @JsonProperty
  @ApiModelProperty
  private String locale;

  @JsonProperty
  @ApiModelProperty
  private long modified;

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

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getPasswordSalt() {
    return passwordSalt;
  }

  public void setPasswordSalt(String passwordSalt) {
    this.passwordSalt = passwordSalt;
  }

  public String getPicture() {
    return picture;
  }

  public void setPicture(String picture) {
    this.picture = picture;
  }

  public String getZoneInfo() {
    return zoneInfo;
  }

  public void setZoneInfo(String zoneInfo) {
    this.zoneInfo = zoneInfo;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
