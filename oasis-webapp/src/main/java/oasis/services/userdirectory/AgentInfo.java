package oasis.services.userdirectory;

import java.util.concurrent.TimeUnit;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import oasis.jongo.etag.HasModified;
import oasis.model.accounts.AgentAccount;
import oasis.model.social.Identity;

public class AgentInfo implements HasModified {
  private static final DateTimeFormatter BIRTHDATE_FORMATTER = ISODateTimeFormat.date().withDefaultYear(0);

  // Profile
  private String name;
  private String family_name;
  private String given_name;
  private String middle_name;
  private String nickname;
  private String picture;
  private String gender;
  private String birthdate;
  private String zoneinfo;
  private String locale;
  private Long updated_at;
  // Email
  private String email;
  private Boolean email_verified;
  // Address
  private Address address;
  // Phone
  private String phone;
  private Boolean phone_verified;

  // account info
  private String organizationId;
  private Boolean admin;
  private long modified;
  private String id;

  public AgentInfo() {
  }

  public AgentInfo(AgentAccount agentAccount, Identity identity) {

    this.id = agentAccount.getId();
    this.admin = agentAccount.isAdmin();
    this.organizationId = agentAccount.getOrganizationId();
    this.modified = agentAccount.getModified();

    // Copy agent infos
    this.picture = agentAccount.getPicture();
    this.zoneinfo = agentAccount.getZoneInfo();
    this.locale = agentAccount.getLocale();
    this.email = agentAccount.getEmailAddress();
    this.email_verified = true; // A agent account is created only

    // Copy identity infos
    if (identity != null) {
      this.name = identity.getName();
      this.family_name = identity.getFamilyName();
      this.given_name = identity.getGivenName();
      this.middle_name = identity.getMiddleName();
      this.nickname = identity.getNickname();
      this.gender = identity.getGender();
      this.birthdate = (identity.getBirthdate() != null ? identity.getBirthdate().toString(BIRTHDATE_FORMATTER) : null);
      this.phone = identity.getPhoneNumber();
      this.phone_verified = identity.isPhoneNumberVerified();

      // Copy address infos
      if (identity.getAddress() != null) {
        this.address = new Address(identity.getAddress());
      }
    }

    long updatedAt = Math.max(agentAccount.getModified(), (identity == null ? 0 : identity.getUpdatedAt()));

    if (updatedAt > 0) {
      this.updated_at = TimeUnit.MILLISECONDS.toSeconds(updatedAt);
    }

  }

  public String getName() {
    return name;
  }

  public AgentInfo setName(String name) {
    this.name = name;
    return this;
  }

  public String getFamily_name() {
    return family_name;
  }

  public AgentInfo setFamily_name(String family_name) {
    this.family_name = family_name;
    return this;
  }

  public String getGiven_name() {
    return given_name;
  }

  public AgentInfo setGiven_name(String given_name) {
    this.given_name = given_name;
    return this;
  }

  public String getMiddle_name() {
    return middle_name;
  }

  public AgentInfo setMiddle_name(String middle_name) {
    this.middle_name = middle_name;
    return this;
  }

  public String getNickname() {
    return nickname;
  }

  public AgentInfo setNickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  public String getPicture() {
    return picture;
  }

  public AgentInfo setPicture(String picture) {
    this.picture = picture;
    return this;
  }

  public String getGender() {
    return gender;
  }

  public AgentInfo setGender(String gender) {
    this.gender = gender;
    return this;
  }

  public String getBirthdate() {
    return birthdate;
  }

  public AgentInfo setBirthdate(String birthdate) {
    this.birthdate = birthdate;
    return this;
  }

  public String getZoneinfo() {
    return zoneinfo;
  }

  public AgentInfo setZoneinfo(String zoneinfo) {
    this.zoneinfo = zoneinfo;
    return this;
  }

  public String getLocale() {
    return locale;
  }

  public AgentInfo setLocale(String locale) {
    this.locale = locale;
    return this;
  }

  public Long getUpdated_at() {
    return updated_at;
  }

  public AgentInfo setUpdated_at(Long updated_at) {
    this.updated_at = updated_at;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public AgentInfo setEmail(String email) {
    this.email = email;
    return this;
  }

  public Boolean isEmail_verified() {
    return email_verified;
  }

  public AgentInfo setEmail_verified(Boolean email_verified) {
    this.email_verified = email_verified;
    return this;
  }

  public Address getAddress() {
    return address;
  }

  public AgentInfo setAddress(Address address) {
    this.address = address;
    return this;
  }

  public String getPhone() {
    return phone;
  }

  public AgentInfo setPhone(String phone) {
    this.phone = phone;
    return this;
  }

  public Boolean isPhone_verified() {
    return phone_verified;
  }

  public AgentInfo setPhone_verified(Boolean phone_verified) {
    this.phone_verified = phone_verified;
    return this;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public AgentInfo setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
    return this;
  }

  public Boolean isAdmin() {
    return admin;
  }

  public AgentInfo setAdmin(Boolean admin) {
    this.admin = admin;
    return this;
  }

  public String getId() {
    return id;
  }

  public AgentInfo setId(String id) {
    this.id = id;
    return this;
  }

  @Override
  public long getModified() {
    return this.modified;
  }

  public AgentInfo setModified(long modified) {
    this.modified = modified;
    return this;
  }

  public static class Address {
    private String street_address;
    private String locality;
    private String region;
    private String postal_code;
    private String country;

    private Address() {
    }

    private Address(oasis.model.social.Address other) {
      this.street_address = other.getStreetAddress();
      this.locality = other.getLocality();
      this.region = other.getRegion();
      this.country = other.getCountry();
      this.postal_code = other.getPostalCode();
    }

    public String getStreetAddress() {
      return street_address;
    }

    public Address setStreetAddress(String streetAddress) {
      this.street_address = streetAddress;
      return this;
    }

    public String getLocality() {
      return locality;
    }

    public Address setLocality(String locality) {
      this.locality = locality;
      return this;
    }

    public String getRegion() {
      return region;
    }

    public Address setRegion(String region) {
      this.region = region;
      return this;
    }

    public String getPostalCode() {
      return postal_code;
    }

    public Address setPostalCode(String postalCode) {
      this.postal_code = postalCode;
      return this;
    }

    public String getCountry() {
      return country;
    }

    public Address setCountry(String country) {
      this.country = country;
      return this;
    }
  }
}
