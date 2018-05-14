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
package oasis.web.openidconnect;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.icu.util.ULocale;

import oasis.model.authz.Scopes;
import oasis.urls.Urls;
import oasis.web.authn.CheckSessionIframePage;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.LogoutPage;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.IntrospectionEndpoint;
import oasis.web.authz.KeysEndpoint;
import oasis.web.authz.RevokeEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.i18n.LocaleHelper;
import oasis.web.userinfo.UserInfoEndpoint;

/**
 * See <a href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig">OpenID Connect Discovery 1.0</a>
 */
@Path("/.well-known/openid-configuration")
public class OpenIdProviderConfigurationEndpoint {
  private static final String[] UI_LOCALES_SUPPORTED = LocaleHelper.SUPPORTED_LOCALES.stream()
      .map(ULocale::toLanguageTag)
      .toArray(String[]::new);

  @Inject Urls urls;

  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Configuration get() {
    return new Configuration();
  }

  private URI getBaseUri() {
    if (urls.canonicalBaseUri().isPresent()) {
      return urls.canonicalBaseUri().get();
    }
    return uriInfo.getBaseUri();
  }

  private UriBuilder getBaseUriBuilder() {
    if (urls.canonicalBaseUri().isPresent()) {
      return UriBuilder.fromUri(urls.canonicalBaseUri().get());
    }
    return uriInfo.getBaseUriBuilder();
  }

  /**
   * See <a href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenId Provider Metadata</a>
   */
  public class Configuration {
    @JsonProperty String issuer = getBaseUri().toString();
    @JsonProperty String authorization_endpoint = getBaseUriBuilder().path(AuthorizationEndpoint.class).build().toString();
    @JsonProperty String token_endpoint = getBaseUriBuilder().path(TokenEndpoint.class).build().toString();
    @JsonProperty String userinfo_endpoint = getBaseUriBuilder().path(UserInfoEndpoint.class).build().toString();
    @JsonProperty String jwks_uri = getBaseUriBuilder().path(KeysEndpoint.class).build().toString();
    // registration_endpoint
    @JsonProperty String[] scopes_supported = { Scopes.OPENID, Scopes.PROFILE, Scopes.EMAIL, Scopes.ADDRESS, Scopes.PHONE, Scopes.OFFLINE_ACCESS };
    /** See {@link AuthorizationEndpoint#validateResponseTypeAndMode}. */
    @JsonProperty String[] response_types_supported = { "code" };
    /** See {@link AuthorizationEndpoint#generateAuthorizationCodeAndRedirect}. */
    @JsonProperty String[] response_modes_supported = { "query" };
    /** See {@link TokenEndpoint#validate}. */
    @JsonProperty String[] grant_types_supported = { "authorization_code", "refresh_token", "urn:ietf:params:oauth:grant-type:jwt-bearer" };
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
    /** See {@link ClientAuthenticationFilter}. */
    @JsonProperty String[] token_endpoint_auth_methods_supported = { "client_secret_basic" };
    // token_endpoint_auth_signing_alg_values_supported
    // display_values_supported, claim_types_supported, claims_supported
    @JsonProperty String service_documentation = urls.developerDoc().map(URI::toString).orElse(null);
    // claims_locales_supported
    @JsonProperty String[] ui_locales_supported = UI_LOCALES_SUPPORTED;
    @JsonProperty boolean claims_parameter_supported = true;
    // This is the default value: @JsonProperty boolean request_parameter_supported = false;
    @JsonProperty boolean request_uri_parameter_supported = false;
    // This is the default value: @JsonProperty boolean require_request_uri_registration = false;
    @JsonProperty String op_policy_uri = urls.privacyPolicy().map(URI::toString).orElse(null);
    @JsonProperty String op_tos_uri = urls.termsOfService().map(URI::toString).orElse(null);

    // See http://openid.net/specs/openid-connect-session-1_0.html#EndpointDiscovery
    @JsonProperty String check_session_iframe = getBaseUriBuilder().path(CheckSessionIframePage.class).build().toString();
    @JsonProperty String end_session_endpoint = getBaseUriBuilder().path(LogoutPage.class).build().toString();

    // See https://tools.ietf.org/html/draft-ietf-oauth-discovery-01
    @JsonProperty String revocation_endpoint = getBaseUriBuilder().path(RevokeEndpoint.class).build().toString();
    /** See {@link ClientAuthenticationFilter}. */
    @JsonProperty String[] revocation_endpoint_auth_methods_supported = { "client_secret_basic" };
    @JsonProperty String introspection_endpoint = getBaseUriBuilder().path(IntrospectionEndpoint.class).build().toString();
    /** See {@link ClientAuthenticationFilter}. */
    @JsonProperty String[] introspection_endpoint_auth_methods_supported = { "client_secret_basic" };
  }
}
