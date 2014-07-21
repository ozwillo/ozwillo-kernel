package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserAccount extends Account {
  @JsonProperty
  private String emailAddress;

  @JsonProperty
  private String identityId;

  @JsonProperty
  private String password;

  @JsonProperty
  private String passwordSalt;

  @JsonProperty
  private String picture;

  @JsonProperty
  private String zoneInfo;

  @JsonProperty
  private String locale;

  @JsonProperty
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
