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

import javax.annotation.Nonnull;

public class Address {
  private String street_address;

  private String locality;

  private String region;

  private String postal_code;

  private String country;

  public Address() {
  }

  public Address(@Nonnull Address other) {
    this.street_address = other.getStreet_address();
    this.locality = other.getLocality();
    this.region = other.getRegion();
    this.postal_code = other.getPostal_code();
    this.country = other.getCountry();
  }

  public String getStreet_address() {
    return street_address;
  }

  public void setStreet_address(String street_address) {
    this.street_address = street_address;
  }

  public String getLocality() {
    return locality;
  }

  public void setLocality(String locality) {
    this.locality = locality;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getPostal_code() {
    return postal_code;
  }

  public void setPostal_code(String postal_code) {
    this.postal_code = postal_code;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }
}
