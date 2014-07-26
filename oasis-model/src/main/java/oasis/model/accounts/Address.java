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
