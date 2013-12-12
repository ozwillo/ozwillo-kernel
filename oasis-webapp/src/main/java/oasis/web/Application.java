package oasis.web;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;

import oasis.web.apidocs.ApiDeclarationProvider;
import oasis.web.apps.ApplicationDirectoryEndpoint;
import oasis.web.apps.ApplicationMarketEndpoint;
import oasis.web.apps.DataProviderDirectoryEndpoint;
import oasis.web.apps.ServiceProviderDirectoryEndpoint;
import oasis.web.auditlog.AuditLogEndpoint;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.Login;
import oasis.web.authn.Logout;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.IntrospectionEndpoint;
import oasis.web.authz.KeysEndpoint;
import oasis.web.authz.RevokeEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.eventbus.EventBusEndpoint;
import oasis.web.example.OpenIdConnect;
import oasis.web.kibana.ElasticSearchProxy;
import oasis.web.kibana.Kibana;
import oasis.web.notifications.NotificationEndpoint;
import oasis.web.providers.CookieParserRequestFilter;
import oasis.web.providers.HttpInterceptor;
import oasis.web.providers.JacksonContextResolver;
import oasis.web.providers.SecureFilter;
import oasis.web.providers.UriParamConverterProvider;
import oasis.web.userdirectory.UserDirectoryEndpoint;
import oasis.web.userinfo.UserInfoEndpoint;
import oasis.web.view.HandlebarsBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
        // Hacks and workarounds
        UriParamConverterProvider.class,
        CookieParserRequestFilter.class,
        SecureFilter.class,
        // Providers
        JacksonContextResolver.class,
        // Views
        HandlebarsBodyWriter.class,
        // Swagger
        ResourceListingProvider.class,
        ApiListingResourceJSON.class,
        SwaggerUI.class,
        ApiDeclarationProvider.class, // Note: this is our own implementation
        // Authentication
        UserAuthenticationFilter.class,
        ClientAuthenticationFilter.class,
        OAuthAuthenticationFilter.class,
        Login.class,
        Logout.class,
        // Authorization
        AuthorizationEndpoint.class,
        TokenEndpoint.class,
        RevokeEndpoint.class,
        KeysEndpoint.class,
        IntrospectionEndpoint.class,
        // UserInfo
        UserInfoEndpoint.class,
        // AuditLog
        AuditLogEndpoint.class,
        HttpInterceptor.class,
        // Notification
        NotificationEndpoint.class,
        // EventBus
        EventBusEndpoint.class,
        // Resources
        Home.class,
        UserDirectoryEndpoint.class,
        OpenIdConnect.class,
        ApplicationDirectoryEndpoint.class,
        ApplicationMarketEndpoint.class,
        ServiceProviderDirectoryEndpoint.class,
        DataProviderDirectoryEndpoint.class,
        // Kibana and ElasticSearch proxy
        Kibana.class,
        ElasticSearchProxy.class
        );
  }
}
