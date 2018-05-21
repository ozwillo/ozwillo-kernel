/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.model.accounts;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.ibm.icu.util.ULocale;

import oasis.model.annotations.Id;

public class UserAccount {
  @Id
  private String id;

  private String email_address;

  private Boolean email_verified;

  private ULocale locale;

  private String given_name;

  private String family_name;

  private String middle_name;

  private String nickname;

  private String gender;

  @JsonProperty
  @JsonFormat(shape=JsonFormat.Shape.STRING)
  private LocalDate birthdate;

  private String phone_number;

  private Boolean phone_number_verified;

  private Address address;

  private String franceconnect_sub;

  private long updated_at;

  private Long created_at;

  public UserAccount() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public UserAccount(@Nonnull UserAccount other) {
    email_address = other.getEmail_address();
    email_verified = other.getEmail_verified();
    locale = other.getLocale();
    given_name = other.getGiven_name();
    family_name = other.getFamily_name();
    middle_name = other.getMiddle_name();
    nickname = other.getNickname();
    gender = other.getGender();
    birthdate = other.getBirthdate();
    phone_number = other.getPhone_number();
    phone_number_verified = other.getPhone_number_verified();
    address = other.getAddress() == null ? null : new Address(other.getAddress());
    franceconnect_sub = other.getFranceconnect_sub();
    updated_at = other.getUpdated_at();
    created_at = other.getCreated_at();
  }

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

  public Boolean getEmail_verified() {
    return email_verified;
  }

  public void setEmail_verified(Boolean email_address_verified) {
    this.email_verified = email_address_verified;
  }

  public ULocale getLocale() {
    return locale == null ? ULocale.ROOT : locale;
  }

  public void setLocale(ULocale locale) {
    this.locale = locale;
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

  public Boolean getPhone_number_verified() {
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

  public String getFranceconnect_sub() {
    return franceconnect_sub;
  }

  public void setFranceconnect_sub(String franceconnect_sub) {
    this.franceconnect_sub = franceconnect_sub;
  }

  public long getUpdated_at() {
    return updated_at;
  }

  public void setUpdated_at(long updated_at) {
    this.updated_at = updated_at;
  }

  public Long getCreated_at() {
    return created_at;
  }

  public void setCreated_at(Long created_at) {
    this.created_at = created_at;
  }


  @JsonIgnore
  public String getName() {
    return Stream.of(getGiven_name(), getMiddle_name(), getFamily_name())
        .filter(s -> !Strings.isNullOrEmpty(s))
        .collect(collectingAndThen(joining(" "), Strings::emptyToNull));
  }

  @JsonIgnore
  public String getDisplayName() {
    String lName = getName();
    if (lName != null && !lName.isEmpty()) {
      return lName;
    }
    return getNickname();
  }
}
