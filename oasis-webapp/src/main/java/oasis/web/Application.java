/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
import oasis.web.authn.ActivateAccountPage;
import oasis.web.authn.ChangePasswordPage;
import oasis.web.authn.ClientAuthenticationFilter;
import oasis.web.authn.ForgotPasswordPage;
import oasis.web.authn.LoginPage;
import oasis.web.authn.LogoutPage;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthFilter;
import oasis.web.authn.ResetPasswordPage;
import oasis.web.authn.SignUpPage;
import oasis.web.authn.UserAuthenticationFilter;
import oasis.web.authn.UserCanonicalBaseUriFilter;
import oasis.web.authn.UserFilter;
import oasis.web.authz.AuthorizationEndpoint;
import oasis.web.authz.IntrospectionEndpoint;
import oasis.web.authz.KeysEndpoint;
import oasis.web.authz.RevokeEndpoint;
import oasis.web.authz.TokenEndpoint;
import oasis.web.eventbus.EventBusEndpoint;
import oasis.web.notifications.NotificationEndpoint;
import oasis.web.openidconnect.OpenIdProviderConfigurationEndpoint;
import oasis.web.providers.JacksonJsonProvider;
import oasis.web.providers.LocaleParamConverterProvider;
import oasis.web.providers.UriParamConverterProvider;
import oasis.web.security.SecureFilter;
import oasis.web.security.StrictRefererFilter;
import oasis.web.status.StatusEndpoint;
import oasis.web.userdirectory.MembershipEndpoint;
import oasis.web.userdirectory.MembershipInvitationPage;
import oasis.web.userdirectory.OrganizationEndpoint;
import oasis.web.userdirectory.OrganizationMembershipEndpoint;
import oasis.web.userdirectory.OrganizationPendingMembershipEndpoint;
import oasis.web.userdirectory.PendingMembershipEndpoint;
import oasis.web.userdirectory.UserDirectoryEndpoint;
import oasis.web.userdirectory.UserEndpoint;
import oasis.web.userdirectory.UserMembershipEndpoint;
import oasis.web.userinfo.UserInfoEndpoint;
import oasis.web.view.SoyTemplateBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
        // Hacks and workarounds
        UriParamConverterProvider.class,
        // Providers
        LocaleParamConverterProvider.class,
        JacksonJsonProvider.class, // Note: this is our own implementation
        // Views
        SoyTemplateBodyWriter.class,
        // Swagger
        ResourceListingProvider.class,
        ApiListingResourceJSON.class,
        SwaggerUI.class,
        ApiDeclarationProvider.class, // Note: this is our own implementation
        // Status
        StatusEndpoint.class,
        // Authentication
        UserCanonicalBaseUriFilter.class,
        UserFilter.class,
        UserAuthenticationFilter.class,
        ClientAuthenticationFilter.class,
        OAuthFilter.class,
        OAuthAuthenticationFilter.class,
        LoginPage.class,
        LogoutPage.class,
        SignUpPage.class,
        ActivateAccountPage.class,
        ChangePasswordPage.class,
        ForgotPasswordPage.class,
        ResetPasswordPage.class,
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
        OrganizationEndpoint.class,
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
        MembershipInvitationPage.class,
        OrganizationPendingMembershipEndpoint.class,
        PendingMembershipEndpoint.class
        );
  }
}
