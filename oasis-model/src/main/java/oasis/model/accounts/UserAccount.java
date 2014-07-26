package oasis.model.accounts;

import org.joda.time.LocalDate;

import oasis.model.annotations.Id;

public class UserAccount {
  @Id
  private String id;

  private String email_address;

  private String picture;

  private String zoneinfo;

  private String locale;

  private String name;

  private String given_name;

  private String family_name;

  private String middle_name;

  private String nickname;

  private String gender;

  private LocalDate birthdate;

  private String phone_number;

  private boolean phone_number_verified;

  private Address address;

  private long updated_at;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEmail_address() {
    return email_address;
  }

  public void setEmail_address(String email_address) {
    this.email_address = email_address;
  }

  public String getPicture() {
    return picture;
  }

  public void setPicture(String picture) {
    this.picture = picture;
  }

  public String getZoneinfo() {
    return zoneinfo;
  }

  public void setZoneinfo(String zoneinfo) {
    this.zoneinfo = zoneinfo;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGiven_name() {
    return given_name;
  }

  public void setGiven_name(String given_name) {
    this.given_name = given_name;
  }

  public String getFamily_name() {
    return family_name;
  }

  public void setFamily_name(String family_name) {
    this.family_name = family_name;
  }

  public String getMiddle_name() {
    return middle_name;
  }

  public void setMiddle_name(String middle_name) {
    this.middle_name = middle_name;
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

  public String getPhone_number() {
    return phone_number;
  }

  public void setPhone_number(String phone_number) {
    this.phone_number = phone_number;
  }

  public boolean isPhone_number_verified() {
    return phone_number_verified;
  }

  public void setPhone_number_verified(Boolean phone_number_verified) {
    this.phone_number_verified = phone_number_verified;
  }

  public Address getAddress() {
    return address;
  }

  public void setAddress(Address address) {
    this.address = address;
  }

  public long getUpdated_at() {
    return updated_at;
  }

  public void setUpdated_at(long updated_at) {
    this.updated_at = updated_at;
  }
}
