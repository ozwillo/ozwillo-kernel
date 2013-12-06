package oasis.model.social;

import javax.annotation.Nonnull;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("identity")
public class Identity {
  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
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
  @ApiModelProperty(required = true)
  private LocalDate birthdate;

  @JsonProperty
  @ApiModelProperty
  private String phoneNumber;

  @JsonProperty
  @ApiModelProperty
  private boolean phoneNumberVerified;

  @JsonProperty
  @ApiModelProperty
  private Address address;

  @JsonProperty
  @ApiModelProperty
  private long updatedAt;

  public Identity() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Identity(@Nonnull Identity other) {
    if (other.getAddress() != null) {
      this.address = new Address(other.getAddress());
    }
    this.name = other.getName();
    this.givenName = other.getGivenName();
    this.familyName = other.getFamilyName();
    this.middleName = other.getMiddleName();
    this.nickname = other.getNickname();
    this.gender = other.getGender();
    this.birthdate = other.getBirthdate();
    this.phoneNumber = other.getPhoneNumber();
    this.phoneNumberVerified = other.isPhoneNumberVerified();
    this.updatedAt = other.getUpdatedAt();
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

  public LocalDate getBirthdate() {
    return birthdate;
  }

  public void setBirthdate(LocalDate birthdate) {
    this.birthdate = birthdate;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public boolean isPhoneNumberVerified() {
    return phoneNumberVerified;
  }

  public void setPhoneNumberVerified(boolean phoneNumberVerified) {
    this.phoneNumberVerified = phoneNumberVerified;
  }

  public Address getAddress() {
    return address;
  }

  public void setAddress(Address address) {
    this.address = address;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
  }
}
