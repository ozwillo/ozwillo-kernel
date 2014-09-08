package oasis.web;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;

import oasis.web.apidocs.ApiDeclarationProvider;
import oasis.web.applications.AccessControlEntryEndpoint;
import oasis.web.applications.AppInstanceAccessControlEndpoint;
import oasis.web.applications.AppInstanceEndpoint;
import oasis.web.applications.ApplicationEndpoint;
import oasis.web.applications.InstanceRegistrationEndpoint;
import oasis.web.applications.MarketBuyEndpoint;
import oasis.web.applications.MarketSearchEndpoint;
import oasis.web.applications.OrganizationAppInstanceEndpoint;
import oasis.web.applications.ServiceEndpoint;
import oasis.web.applications.ServiceSubscriptionEndpoint;
import oasis.web.applications.SubscriptionEndpoint;
import oasis.web.applications.UserAppInstanceEndpoint;
import oasis.web.applications.UserSubscriptionEndpoint;
import oasis.web.auditlog.AuditLogEndpoint;
import oasis.web.auditlog.HttpInterceptor;
import oasis.web.authn.ChangePasswordPage;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.LoginPage;
import oasis.web.authn.LogoutPage;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthFilter;
import oasis.web.authn.SignUpPage;
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
import oasis.web.userdirectory.MembershipEndpoint;
import oasis.web.userdirectory.OrganizationMembershipEndpoint;
import oasis.web.userdirectory.UserDirectoryEndpoint;
import oasis.web.userdirectory.UserEndpoint;
import oasis.web.userdirectory.UserMembershipEndpoint;
import oasis.web.userinfo.UserInfoEndpoint;
import oasis.web.view.SoyTofuBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
        // Hacks and workarounds
        UriParamConverterProvider.class,
        // Providers
        JacksonJsonProvider.class, // Note: this is our own implementation
        // Views
        SoyTofuBodyWriter.class,
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
        OAuthFilter.class,
        OAuthAuthenticationFilter.class,
        LoginPage.class,
        LogoutPage.class,
        SignUpPage.class,
        ChangePasswordPage.class,
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
        UserEndpoint.class,
        MembershipEndpoint.class,
        UserMembershipEndpoint.class,
        OrganizationMembershipEndpoint.class,
        MarketSearchEndpoint.class,
        MarketBuyEndpoint.class,
        InstanceRegistrationEndpoint.class,
        ApplicationEndpoint.class,
        AppInstanceEndpoint.class,
        ServiceEndpoint.class,
        ServiceSubscriptionEndpoint.class,
        SubscriptionEndpoint.class,
        UserSubscriptionEndpoint.class,
        OrganizationAppInstanceEndpoint.class,
        UserAppInstanceEndpoint.class,
        AccessControlEntryEndpoint.class,
        AppInstanceAccessControlEndpoint.class,
        // Kibana and ElasticSearch proxy
        Kibana.class,
        ElasticSearchProxy.class
        );
  }
}
