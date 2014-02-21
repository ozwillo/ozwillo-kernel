package oasis.web.account;

import javax.annotation.Nonnull;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.social.Address;
import oasis.model.social.Identity;

class ProfileInfo {
  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty
  private String name;
  @JsonProperty
  @ApiModelProperty
  private String givenName;
  @JsonProperty
  @ApiModelProperty
  private String familyName;
  @JsonProperty
  @ApiModelProperty
  private String middleName;
  @JsonProperty
  @ApiModelProperty
  private String nickname;
  @JsonProperty
  @ApiModelProperty
  private String gender;
  @JsonProperty
  @ApiModelProperty
  private String birthdate;
  @JsonProperty
  @ApiModelProperty
  private String phoneNumber;
  @JsonProperty
  @ApiModelProperty
  private Address address;

  public ProfileInfo() {}

  ProfileInfo(@Nonnull Identity identity) {
    this.id = identity.getId();
    this.name = identity.getName();
    this.givenName = identity.getGivenName();
    this.familyName = identity.getFamilyName();
    this.middleName = identity.getMiddleName();
    this.nickname = identity.getNickname();
    this.gender = identity.getGender();
    if (identity.getBirthdate() != null) {
      this.birthdate = identity.getBirthdate().toString();
    }
    this.phoneNumber = identity.getPhoneNumber();
    this.address = identity.getAddress();
  }

  public Identity toIdentity() {
    Identity identity = new Identity();
    identity.setId(this.id);
    identity.setName(this.name);
    identity.setGivenName(this.givenName);
    identity.setFamilyName(this.familyName);
    identity.setMiddleName(this.middleName);
    identity.setNickname(this.nickname);
    identity.setGender(this.gender);
    if (!Strings.isNullOrEmpty(this.birthdate)) {
      identity.setBirthdate(LocalDate.parse(this.birthdate));
    }
    identity.setPhoneNumber(this.phoneNumber);
    identity.setAddress(this.address);
    return identity;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public String getMiddleName() {
    return middleName;
  }

  public void setMiddleName(String middleName) {
    this.middleName = middleName;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public String getBirthdate() {
    return birthdate;
  }

  public void setBirthdate(String birthdate) {
    this.birthdate = birthdate;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public Address getAddress() {
    return address;
  }

  public void setAddress(Address address) {
    this.address = address;
  }
}
