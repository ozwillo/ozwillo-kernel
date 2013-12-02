package oasis.web.userinfo;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.Key;
import com.google.common.base.Splitter;

import oasis.openidconnect.OpenIdConnectModule;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;

@Authenticated @OAuth
@Path("/u/userinfo")
public class UserInfoEndpoint {
  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();
  private static final JsonWebSignature.Header JWS_HEADER = new JsonWebSignature.Header().setType("JWS").setAlgorithm("RS256");
  private static final String APPLICATION_JWT = "application/jwt";

  @Context SecurityContext securityContext;
  @Inject OpenIdConnectModule.Settings settings;
  @Inject JsonFactory jsonFactory;

  @GET
  @Produces(APPLICATION_JWT)
  public Response getSigned(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader) throws GeneralSecurityException, IOException {
    // TODO: Validate the access token

    UserInfo userInfo = getUserInfo();
    userInfo.setSubject(securityContext.getUserPrincipal().getName());

    String signedJwt = JsonWebSignature.signUsingRsaSha256(
        settings.keyPair.getPrivate(),
        jsonFactory,
        JWS_HEADER,
        userInfo
    );
    return Response.ok().entity(signedJwt).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getUnsigned(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader) throws IOException {
    // TODO: Validate the access token

    UserInfo userInfo = getUserInfo();
    userInfo.setSubject(securityContext.getUserPrincipal().getName());

    String json = jsonFactory.toString(userInfo);
    return Response.ok().entity(json).build();
  }

  @POST
  @Produces("application/jwt")
  public Response postSigned(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader) throws GeneralSecurityException, IOException {
    return getSigned(authorizationHeader);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public Response postUnsigned(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader) throws IOException {
    return getUnsigned(authorizationHeader);
  }

  private UserInfo getUserInfo() {
    UserInfo userInfo = new UserInfo();

    // TODO: Fill the UserInfo instance

    return userInfo;
  }

  private Response insufficientScopeResponse() {
    return errorResponse(Response.Status.FORBIDDEN, "insufficient_scope");
  }

  private Response invalidTokenResponse() {
    return errorResponse(Response.Status.UNAUTHORIZED, "invalid_token");
  }

  private Response errorResponse(Response.Status status, String errorCode) {
    return Response.status(status).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\"").build();
  }

  private static class UserInfo extends JsonWebToken.Payload {
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

    public String getName() {
      return name;
    }

    public UserInfo setName(String name) {
      this.name = name;
      return this;
    }

    public String getFamilyName() {
      return family_name;
    }

    public UserInfo setFamilyName(String familyName) {
      this.family_name = familyName;
      return this;
    }

    public String getGivenName() {
      return given_name;
    }

    public UserInfo setGivenName(String givenName) {
      this.given_name = givenName;
      return this;
    }

    public String getMiddleName() {
      return middle_name;
    }

    public UserInfo setMiddleName(String middle_name) {
      this.middle_name = middle_name;
      return this;
    }

    public String getNickname() {
      return nickname;
    }

    public UserInfo setNickname(String nickname) {
      this.nickname = nickname;
      return this;
    }

    public String getPicture() {
      return picture;
    }

    public UserInfo setPicture(String picture) {
      this.picture = picture;
      return this;
    }

    public String getGender() {
      return gender;
    }

    public UserInfo setGender(String gender) {
      this.gender = gender;
      return this;
    }

    public String getBirthdate() {
      return birthdate;
    }

    public UserInfo setBirthdate(String birthdate) {
      this.birthdate = birthdate;
      return this;
    }

    public String getZoneinfo() {
      return zoneinfo;
    }

    public UserInfo setZoneinfo(String zoneinfo) {
      this.zoneinfo = zoneinfo;
      return this;
    }

    public String getLocale() {
      return locale;
    }

    public UserInfo setLocale(String locale) {
      this.locale = locale;
      return this;
    }

    public Long getUpdatedAt() {
      return updated_at;
    }

    public UserInfo setUpdatedAt(Long updated_at) {
      this.updated_at = updated_at;
      return this;
    }

    public String getEmail() {
      return email;
    }

    public UserInfo setEmail(String email) {
      this.email = email;
      return this;
    }

    public Boolean isEmailVerified() {
      return email_verified;
    }

    public UserInfo setEmailVerified(Boolean email_verified) {
      this.email_verified = email_verified;
      return this;
    }

    public Address getAddress() {
      return address;
    }

    public UserInfo setAddress(Address address) {
      this.address = address;
      return this;
    }

    public String getPhone() {
      return phone;
    }

    public UserInfo setPhone(String phone) {
      this.phone = phone;
      return this;
    }

    public Boolean isPhoneVerified() {
      return phone_verified;
    }

    public UserInfo setPhoneVerified(Boolean phone_verified) {
      this.phone_verified = phone_verified;
      return this;
    }

    @Override
    public UserInfo setExpirationTimeSeconds(Long expirationTimeSeconds) {
      super.setExpirationTimeSeconds(expirationTimeSeconds);
      return this;
    }

    @Override
    public UserInfo setNotBeforeTimeSeconds(Long notBeforeTimeSeconds) {
      super.setNotBeforeTimeSeconds(notBeforeTimeSeconds);
      return this;
    }

    @Override
    public UserInfo setIssuedAtTimeSeconds(Long issuedAtTimeSeconds) {
      super.setIssuedAtTimeSeconds(issuedAtTimeSeconds);
      return this;
    }

    @Override
    public UserInfo setIssuer(String issuer) {
      super.setIssuer(issuer);
      return this;
    }

    @Override
    public UserInfo setAudience(Object audience) {
      super.setAudience(audience);
      return this;
    }

    @Override
    public UserInfo setJwtId(String jwtId) {
      super.setJwtId(jwtId);
      return this;
    }

    @Override
    public UserInfo setType(String type) {
      super.setType(type);
      return this;
    }

    @Override
    public UserInfo setSubject(String subject) {
      super.setSubject(subject);
      return this;
    }

    @Override
    public UserInfo set(String fieldName, Object value) {
      super.set(fieldName, value);
      return this;
    }

    private static class Address {
      private String street_address;
      private String locality;
      private String region;
      private String postal_code;
      private String country;

      public String getStreetAddress() {
        return street_address;
      }

      public Address setStreetAddress(String street_address) {
        this.street_address = street_address;
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

      public Address setPostalCode(String postal_code) {
        this.postal_code = postal_code;
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
}
