package oasis.web.userinfo;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.Key;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.AccessToken;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.auth.AuthModule;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.authz.KeysEndpoint;
import oasis.web.resteasy.Resteasy1099;

@Authenticated @OAuth @WithScopes("openid")
@Path("/a/userinfo")
@Api(value = "/a/userinfo", description = "UserInfo Endpoint")
public class UserInfoEndpoint {
  private static final JsonWebSignature.Header JWS_HEADER = new JsonWebSignature.Header()
      .setType("JWS")
      .setAlgorithm("RS256")
      .setKeyId(KeysEndpoint.JSONWEBKEY_PK_ID);
  private static final DateTimeFormatter BIRTHDATE_FORMATTER = ISODateTimeFormat.date().withDefaultYear(0);
  private static final String EMAIL_SCOPE = "email";
  private static final String PROFILE_SCOPE = "profile";
  private static final String PHONE_SCOPE = "phone";
  private static final String ADDRESS_SCOPE = "address";
  /** Note: we'd prefer JWT, but OpenID Connect wants us to prefer JSON, so using qs&lt;1.0 here. */
  private static final String APPLICATION_JWT = "application/jwt; qs=0.99";

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @Inject AuthModule.Settings settings;
  @Inject JsonFactory jsonFactory;
  @Inject AccountRepository accountRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;

  @GET
  @Produces(APPLICATION_JWT)
  @ApiOperation(
      value = "Return Claims about the End-User in signed JWT format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a>, " +
          "the <a href=\"http://tools.ietf.org/html/draft-ietf-oauth-json-web-token-08\">JWT Draft</a> " +
          "and the <a href=\"http://tools.ietf.org/html/draft-ietf-jose-json-web-signature-11\">JWS Draft</a> for more information."
  )
  public Response getSigned() throws GeneralSecurityException, IOException {
    UserInfo userInfo = getUserInfo();
    userInfo.setIssuer(Resteasy1099.getBaseUri(uriInfo).toString());
    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    userInfo.setAudience(accessToken.getServiceProviderId());

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
  @ApiOperation(
      value = "Return Claims about the End-User in JSON format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a> for more information."
  )
  public Response getUnsigned() throws IOException {
    UserInfo userInfo = getUserInfo();

    String json = jsonFactory.toString(userInfo);
    return Response.ok().entity(json).build();
  }

  @POST
  @Produces(APPLICATION_JWT)
  @ApiOperation(
      value = "Return Claims about the End-User in signed JWT format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a>, " +
          "the <a href=\"http://tools.ietf.org/html/draft-ietf-oauth-json-web-token-08\">JWT Draft</a> " +
          "and the <a href=\"http://tools.ietf.org/html/draft-ietf-jose-json-web-signature-11\">JWS Draft</a> for more information."
  )
  public Response postSigned() throws GeneralSecurityException, IOException {
    return getSigned();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Return Claims about the End-User in JSON format.",
      notes = "See the <a href=\"http://openid.net/specs/openid-connect-basic-1_0.html#UserInfo\">OpenID Connect Draft</a> for more information."
  )
  public Response postUnsigned() throws IOException {
    return getUnsigned();
  }

  private UserInfo getUserInfo() {
    OAuthPrincipal oAuthPrincipal = (OAuthPrincipal) securityContext.getUserPrincipal();
    UserAccount userAccount = accountRepository.getUserAccountById(oAuthPrincipal.getAccessToken().getAccountId());

    if (userAccount == null) {
      throw invalidTokenResponse();
    }

    AccessToken accessToken = oAuthPrincipal.getAccessToken();
    assert accessToken != null;

    Set<String> scopeIds = accessToken.getScopeIds();

    UserInfo userInfo = getUserInfo(userAccount, scopeIds);
    userInfo.setSubject(userAccount.getId());
    return userInfo;
  }

  private UserInfo getUserInfo(UserAccount userAccount, Set<String> scopeIds) {
    UserInfo userInfo = new UserInfo();

    if (scopeIds.contains(PROFILE_SCOPE)) {
      String birthDate = userAccount.getBirthdate() != null ? userAccount.getBirthdate().toString(BIRTHDATE_FORMATTER) : null;
      userInfo.setName(userAccount.getName())
          .setFamilyName(userAccount.getFamily_name())
          .setGivenName(userAccount.getGiven_name())
          .setMiddleName(userAccount.getMiddle_name())
          .setNickname(userAccount.getNickname())
          .setPicture(userAccount.getPicture())
          .setGender(userAccount.getGender())
          .setBirthdate(birthDate)
          .setZoneinfo(userAccount.getZoneinfo())
          .setLocale(userAccount.getLocale() == null ? null : userAccount.getLocale().toLanguageTag());
    }

    if (scopeIds.contains(EMAIL_SCOPE) && userAccount.getEmail_address() != null) {
      userInfo.setEmail(userAccount.getEmail_address());
      userInfo.setEmailVerified(userAccount.getEmail_verified());
    }

    if (scopeIds.contains(ADDRESS_SCOPE) && userAccount.getAddress() != null) {
      UserInfo.Address address = new UserInfo.Address()
          .setStreetAddress(userAccount.getAddress().getStreet_address())
          .setLocality(userAccount.getAddress().getLocality())
          .setRegion(userAccount.getAddress().getRegion())
          .setPostalCode(userAccount.getAddress().getPostal_code())
          .setCountry(userAccount.getAddress().getCountry());
      userInfo.setAddress(address);
    }

    if (scopeIds.contains(PHONE_SCOPE) && userAccount.getPhone_number() != null) {
      userInfo.setPhone_number(userAccount.getPhone_number());
      userInfo.setPhone_number_verified(Boolean.TRUE.equals(userAccount.getPhone_number_verified()));
    }

    long updatedAt = userAccount.getUpdated_at();
    if (updatedAt > 0) {
      userInfo.setUpdatedAt(TimeUnit.MILLISECONDS.toSeconds(updatedAt));
    }

    OrganizationMembership membership = organizationMembershipRepository.getOrganizationForUserIfUnique(userAccount.getId());
    if (membership != null) {
      userInfo.setOrganization_admin(membership.isAdmin());
      userInfo.setOrganization_id(membership.getOrganizationId());
    }

    return userInfo;
  }

  private WebApplicationException invalidTokenResponse() {
    return errorResponse(Response.Status.UNAUTHORIZED, "invalid_token");
  }

  private WebApplicationException errorResponse(Response.Status status, String errorCode) {
    return new WebApplicationException(Response.status(status).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\"").build());
  }

  private static class UserInfo extends JsonWebToken.Payload {
    // Profile
    @Key private String name;
    @Key private String family_name;
    @Key private String given_name;
    @Key private String middle_name;
    @Key private String nickname;
    @Key private String picture;
    @Key private String gender;
    @Key private String birthdate;
    @Key private String zoneinfo;
    @Key private String locale;
    @Key private Long updated_at;
    // Email
    @Key private String email;
    @Key private Boolean email_verified;
    // Address
    @Key private Address address;
    // Phone
    @Key private String phone_number;
    @Key private Boolean phone_number_verified;

    // Agent information
    @Key private Boolean organization_admin;
    @Key private String organization_id;

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

    public String getPhone_number() {
      return phone_number;
    }

    public UserInfo setPhone_number(String phone_number) {
      this.phone_number = phone_number;
      return this;
    }

    public Boolean isPhone_number_verified() {
      return phone_number_verified;
    }

    public UserInfo setPhone_number_verified(Boolean phone_verified) {
      this.phone_number_verified = phone_verified;
      return this;
    }

    public Boolean isOrganization_admin() {
      return organization_admin;
    }

    public void setOrganization_admin(Boolean isAdmin) {
      this.organization_admin = isAdmin;
    }

    public String getOrganization_id() {
      return organization_id;
    }

    public void setOrganization_id(String organizationId) {
      this.organization_id = organizationId;
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
      @Key private String street_address;
      @Key private String locality;
      @Key private String region;
      @Key private String postal_code;
      @Key private String country;

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
