package oasis.web.openidconnect;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.web.authn.LogoutPage;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.KeysEndpoint;
import oasis.web.authz.RevokeEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.userinfo.UserInfoEndpoint;

/**
 * See <a href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig">OpenID Connect Discovery 1.0</a>
 */
@Path("/.well-known/openid-configuration")
public class OpenIdProviderConfigurationEndpoint {
  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Configuration get() {
    return new Configuration();
  }

  /**
   * See <a href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OPENId Provider Metadata</a>
   */
  public class Configuration {
    @JsonProperty String issuer = uriInfo.getBaseUri().toString();
    @JsonProperty String authorization_endpoint = uriInfo.getBaseUriBuilder().path(AuthorizationEndpoint.class).build().toString();
    @JsonProperty String token_endpoint = uriInfo.getBaseUriBuilder().path(TokenEndpoint.class).build().toString();
    @JsonProperty String userinfo_endpoint = uriInfo.getBaseUriBuilder().path(UserInfoEndpoint.class).build().toString();
    @JsonProperty String jwks_uri = uriInfo.getBaseUriBuilder().path(KeysEndpoint.class).build().toString();
    // registration_endpoint, scopes_supported
    /** See {@link AuthorizationEndpoint#validateResponseTypeAndMode}. */
    @JsonProperty String[] response_types_supported = { "code" };
    /** See {@link AuthorizationEndpoint#generateAuthorizationCodeAndRedirect}. */
    @JsonProperty String[] response_modes_supported = { "query" };
    /** See {@link TokenEndpoint#validate}. */
    @JsonProperty String[] grant_types_supported = { "authorization_code", "refresh_token" };
    // acr_values_supported
    // TODO: support "pairwise" subject types? How to correlate them in DataCore and other Data Providers then?
    @JsonProperty String[] subject_types_supported = { "public" };
    /** See {@link AuthorizationEndpoint} and {@link TokenEndpoint}. */
    @JsonProperty String[] id_token_signing_alg_values_supported = { "RS256" };
    // id_token_encryption_alg_supported, id_token_encryption_enc_values_supported
    /** See {@link UserInfoEndpoint}. */
    @JsonProperty String[] userinfo_signing_alg_values_supported = { "RS256" };
    // userinfo_encryption_alg_values_supported, userinfo_encryption_enc_values_supported
    // request_object_signing_alg_values_supported, request_object_encryption_alg_values_supported, request_object_encryption_enc_values_supported
    /** See {@link oasis.web.authn.ClientAuthenticationFilter}. */
    @JsonProperty String[] token_endpoint_auth_methods_supported = { "client_secret_basic" };
    // token_endpoint_auth_signing_alg_values_supported
    // display_values_supported, claim_types_supported, claims_supported
    // TODO: service_documentation
    // claims_locales_supported
    // TODO: ui_locales_supported
    // This is the default value: @JsonProperty boolean claims_parameter_supported = false;
    // This is the default value: @JsonProperty boolean request_parameter_supported = false;
    @JsonProperty boolean request_uri_parameter_supported = false;
    // This is the default value: @JsonProperty boolean require_request_uri_registration = false;
    // TODO: op_policy_uri, op_tos_uri

    // See http://openid.net/specs/openid-connect-session-1_0.html#EndpointDiscovery
    // TODO: check_session_iframe
    @JsonProperty String end_session_endpoint = uriInfo.getBaseUriBuilder().path(LogoutPage.class).build().toString();

    // See http://lists.openid.net/pipermail/openid-specs-ab/Week-of-Mon-20140120/004581.html
    @JsonProperty String revocation_endpoint = uriInfo.getBaseUriBuilder().path(RevokeEndpoint.class).build().toString();
  }
}
