package oasis.model.accounts;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("address")
public class Address {
  @JsonProperty
  @ApiModelProperty
  private String streetAddress;

  @JsonProperty
  @ApiModelProperty
  private String locality;

  @JsonProperty
  @ApiModelProperty
  private String region;

  @JsonProperty
  @ApiModelProperty
  private String postalCode;

  @JsonProperty
  @ApiModelProperty
  private String country;

  public Address() {
  }

  public Address(@Nonnull Address other) {
    this.streetAddress = other.getStreetAddress();
    this.locality = other.getLocality();
    this.region = other.getRegion();
    this.postalCode = other.getPostalCode();
    this.country = other.getCountry();
  }

  public String getStreetAddress() {
    return streetAddress;
  }

  public void setStreetAddress(String streetAddress) {
    this.streetAddress = streetAddress;
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

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }
}
