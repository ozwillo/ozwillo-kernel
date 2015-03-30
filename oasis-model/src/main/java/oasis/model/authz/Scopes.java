package oasis.model.authz;

public interface Scopes {
  // OpenID Connect 1.0
  public static final String OPENID = "openid";
  public static final String PROFILE = "profile";
  public static final String EMAIL = "email";
  public static final String ADDRESS = "address";
  public static final String PHONE = "phone";
  public static final String OFFLINE_ACCESS = "offline_access";

  // Portal
  public static final String PORTAL = "portal";
}
