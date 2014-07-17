package oasis.web;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;

import oasis.web.account.ProfilePage;
import oasis.web.apidocs.ApiDeclarationProvider;
import oasis.web.applications.AppInstanceEndpoint;
import oasis.web.applications.ApplicationEndpoint;
import oasis.web.applications.InstanceRegistrationEndpoint;
import oasis.web.applications.MarketBuyEndpoint;
import oasis.web.applications.MarketSearchEndpoint;
import oasis.web.applications.ServiceEndpoint;
import oasis.web.auditlog.AuditLogEndpoint;
import oasis.web.auditlog.HttpInterceptor;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.LoginPage;
import oasis.web.authn.LogoutPage;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authn.UserFilter;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.IntrospectionEndpoint;
import oasis.web.authz.KeysEndpoint;
import oasis.web.authz.RevokeEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.eventbus.EventBusEndpoint;
import oasis.web.kibana.ElasticSearchProxy;
import oasis.web.kibana.Kibana;
import oasis.web.notifications.NotificationEndpoint;
import oasis.web.openidconnect.OpenIdProviderConfigurationEndpoint;
import oasis.web.providers.JacksonJsonProvider;
import oasis.web.providers.UriParamConverterProvider;
import oasis.web.security.SecureFilter;
import oasis.web.security.StrictRefererFilter;
import oasis.web.status.StatusEndpoint;
import oasis.web.userdirectory.UserDirectoryEndpoint;
import oasis.web.userinfo.UserInfoEndpoint;
import oasis.web.view.HandlebarsBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
        // Hacks and workarounds
        UriParamConverterProvider.class,
        // Providers
        JacksonJsonProvider.class, // Note: this is our own implementation
        // Views
        HandlebarsBodyWriter.class,
        // Swagger
        ResourceListingProvider.class,
        ApiListingResourceJSON.class,
        SwaggerUI.class,
        ApiDeclarationProvider.class, // Note: this is our own implementation
        // Status
        StatusEndpoint.class,
        // Authentication
        UserFilter.class,
        UserAuthenticationFilter.class,
        ClientAuthenticationFilter.class,
        OAuthAuthenticationFilter.class,
        LoginPage.class,
        LogoutPage.class,
        // Authorization
        OpenIdProviderConfigurationEndpoint.class,
        AuthorizationEndpoint.class,
        TokenEndpoint.class,
        RevokeEndpoint.class,
        KeysEndpoint.class,
        IntrospectionEndpoint.class,
        // Security
        SecureFilter.class,
        StrictRefererFilter.class,
        // UserInfo
        UserInfoEndpoint.class,
        ProfilePage.class,
        // AuditLog
        AuditLogEndpoint.class,
        HttpInterceptor.class,
        // Notification
        NotificationEndpoint.class,
        // EventBus
        EventBusEndpoint.class,
        // Resources
        StaticResources.class,
        UserDirectoryEndpoint.class,
        MarketSearchEndpoint.class,
        MarketBuyEndpoint.class,
        InstanceRegistrationEndpoint.class,
        ApplicationEndpoint.class,
        AppInstanceEndpoint.class,
        ServiceEndpoint.class,
        // Kibana and ElasticSearch proxy
        Kibana.class,
        ElasticSearchProxy.class
        );
  }
}
