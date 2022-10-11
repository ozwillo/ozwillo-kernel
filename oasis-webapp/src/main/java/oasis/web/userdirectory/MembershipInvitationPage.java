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
package oasis.web.userdirectory;

import java.net.URI;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import oasis.model.applications.v2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;

import oasis.model.DuplicateKeyException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.MembershipInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.branding.BrandHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.AcceptedMembershipInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.RejectedMembershipInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.RejectedMembershipInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationSoyInfo.MembershipInvitationAlreadyMemberErrorSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationSoyInfo.MembershipInvitationSoyTemplateInfo;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.LogoutPage;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/d/invitation/{token}")
@Produces(MediaType.TEXT_HTML)
@Authenticated @User
public class MembershipInvitationPage {
  private static final Logger logger = LoggerFactory.getLogger(MembershipInvitationPage.class);

  @PathParam("token") String serializedToken;

  @Inject AccountRepository accountRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject TokenRepository tokenRepository;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  @Context Request request;
  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Path("")
  public Response showInvitation(@QueryParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount user = accountRepository.getUserAccountById(userId);
    ULocale userLocale = user.getLocale();

    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return generateNotFoundPage(userLocale, brandInfo);
    }

    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository.getPendingOrganizationMembership(
        membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      // The token is not related to a pending membership so it is useless
      // Let's remove it
      tokenRepository.revokeToken(membershipInvitationToken.getId());
      return generateNotFoundPage(userLocale, brandInfo);
    }

    Organization organization = directoryRepository.getOrganization(pendingOrganizationMembership.getOrganizationId());
    if (organization == null) {
      return generateNotFoundPage(userLocale, brandInfo);
    }

    if (organizationMembershipRepository.getOrganizationMembership(userId, organization.getId()) != null) {
      return generateAlreadyMemberErrorPage(user, pendingOrganizationMembership, organization, brandInfo);
    }

    // XXX: if organization is in DELETED status, we allow the user to proceed, just in case it's later moved back to AVAILABLE
    return generatePage(userLocale, pendingOrganizationMembership, organization, brandInfo);
  }

  @POST
  @StrictReferer
  @Path("/accept")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response acceptInvitation(@FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return goBackToFirstStep(brandInfo);
    }

    String currentAccountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository.getPendingOrganizationMembership(
        membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      return goBackToFirstStep(brandInfo);
    }
    OrganizationMembership membership;
    try {
      membership = organizationMembershipRepository
          .acceptPendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId(), currentAccountId);
    } catch (DuplicateKeyException e) {
      return goBackToFirstStep(brandInfo);
    }
    if (membership == null) {
      return goBackToFirstStep(brandInfo);
    }

    try {
      Organization organization = directoryRepository.getOrganization(pendingOrganizationMembership.getOrganizationId());
      if (organization.getStatus() == Organization.Status.AVAILABLE) {
        UserAccount requester = accountRepository.getUserAccountById(pendingOrganizationMembership.getCreator_id());
        notifyRequester(organization, pendingOrganizationMembership.getEmail(), requester.getId(), true);
        notifyAdmins(organization, pendingOrganizationMembership.getEmail(), requester, true);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted organization membership invitation.", e);
    }

    tokenRepository.revokeToken(membershipInvitationToken.getId());

    Iterable<AccessControlEntry> accessControlEntries = accessControlRepository
        .getPendingAccessControlEntriesForUser(pendingOrganizationMembership.getEmail(), pendingOrganizationMembership.getOrganizationId());

    // Automatically redirect to the first service of the first app instance for which the user has been invited
    URI serviceUri = null;
    Iterator<AccessControlEntry> aceIterator = accessControlEntries.iterator();
    if (aceIterator.hasNext()) {
      logger.error("Searching for app instance to redirect to");
      AccessControlEntry ace = aceIterator.next();
      AppInstance appInstance = appInstanceRepository.getAppInstance(ace.getInstance_id());
      logger.error("Got app instance: {}", appInstance.getId());
      Iterator<Service> services = serviceRepository.getServicesOfInstance(appInstance.getId()).iterator();
      if (services.hasNext()) {
        Service service = services.next();
        logger.error("App instance has a service ({}): {}", service.getId(), service.getService_uri());
        serviceUri = URI.create(service.getService_uri());
      }

    }

    // Accept all the application invitations and remove token
    String instanceId = null;
    for (AccessControlEntry ace : accessControlEntries) {
      accessControlRepository.acceptPendingAccessControlEntry(ace.getId(), currentAccountId);
      tokenRepository.revokeInvitationTokensForAppInstance(ace.getId());
      instanceId = ace.getInstance_id();
    }

    if (serviceUri != null)
      return Response.seeOther(serviceUri).build();
    else {
      Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
      return Response.seeOther(
              urls.myNetwork().orElse(uriInfo.getBaseUri())
      ).build();
    }
  }

  @POST
  @StrictReferer
  @Path("/refuse")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response refuseInvitation(@FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return goBackToFirstStep(brandInfo);
    }

    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository
        .getPendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      return goBackToFirstStep(brandInfo);
    }

    boolean deleted = organizationMembershipRepository.deletePendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId());
    if (!deleted) {
      return goBackToFirstStep(brandInfo);
    }

    try {
      Organization organization = directoryRepository.getOrganization(pendingOrganizationMembership.getOrganizationId());
      if (organization.getStatus() == Organization.Status.AVAILABLE) {
        UserAccount requester = accountRepository.getUserAccountById(pendingOrganizationMembership.getCreator_id());
        notifyRequester(organization, pendingOrganizationMembership.getEmail(), requester.getId(), false);
        notifyAdmins(organization, pendingOrganizationMembership.getEmail(), requester, false);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted organization membership invitation.", e);
    }

    tokenRepository.revokeToken(membershipInvitationToken.getId());

    // Refuse all the application invitations and remove token
    Iterable<AccessControlEntry> accessControlEntries = accessControlRepository
        .getPendingAccessControlEntriesForUser(pendingOrganizationMembership.getEmail(), pendingOrganizationMembership.getOrganizationId());
    for (AccessControlEntry ace : accessControlEntries) {
        accessControlRepository.deletePendingAccessControlEntry(ace.getId());
        tokenRepository.revokeInvitationTokensForAppInstance(ace.getId());
    }

    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    return Response.seeOther(
        urls.myNetwork().orElse(uriInfo.getBaseUri())
    ).build();
  }

  private Response generatePage(ULocale locale, OrganizationMembership pendingOrganizationMembership, Organization organization, BrandInfo brandInfo) {
    UserAccount requester = accountRepository.getUserAccountById(pendingOrganizationMembership.getCreator_id());

    Set<String> instanceIds = Streams.stream(
            accessControlRepository.getPendingAccessControlEntriesForUser(pendingOrganizationMembership.getEmail(), pendingOrganizationMembership.getOrganizationId()))
        .map(AccessControlEntry::getInstance_id)
        .collect(Collectors.toSet());

    ImmutableList<String> pendingAppInvitations = Streams.stream(appInstanceRepository.getAppInstances(instanceIds))
        .filter(Objects::nonNull) // that shouldn't happen, but we don't want to break if that's the case
        .map(appInstance -> appInstance.getName().get(locale))
        .sorted(Collator.getInstance(locale))
        .collect(ImmutableList.toImmutableList());

    URI acceptFormAction = uriInfo.getBaseUriBuilder()
        .path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "acceptInvitation")
        .build(serializedToken);
    URI refuseFormAction = uriInfo.getBaseUriBuilder()
        .path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "refuseInvitation")
        .build(serializedToken);
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(
            OrgMembershipInvitationSoyInfo.MEMBERSHIP_INVITATION,
            locale,
            ImmutableMap.<String, Object>builderWithExpectedSize(6)
                .put(MembershipInvitationSoyTemplateInfo.ACCEPT_FORM_ACTION, acceptFormAction.toString())
                .put(MembershipInvitationSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString())
                .put(MembershipInvitationSoyTemplateInfo.ORGANIZATION_NAME, organization.getName())
                .put(MembershipInvitationSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName())
                .put(MembershipInvitationSoyTemplateInfo.INVITED_EMAIL, pendingOrganizationMembership.getEmail())
                .put(MembershipInvitationSoyTemplateInfo.PENDING_APPS, pendingAppInvitations)
                .build(),
            brandInfo
        ))
        .build();
  }

  private Response generateNotFoundPage(ULocale locale, BrandInfo brandInfo) {
    return Response.status(Response.Status.NOT_FOUND)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(OrgMembershipInvitationSoyInfo.MEMBERSHIP_INVITATION_TOKEN_ERROR, locale, brandInfo))
        .build();
  }

  private Response goBackToFirstStep(BrandInfo brandInfo) {
    // The token was expired between showInvitation page loading and form action
    // So let's restart the process by sending the user to the showInvitation page which should display an error
    URI showInvitationUri = uriInfo.getBaseUriBuilder()
        .path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "showInvitation")
        .queryParam(BrandHelper.BRAND_PARAM, brandInfo.getBrand_id())
        .build(serializedToken);
    return Response.seeOther(showInvitationUri).build();
  }

  private Response generateAlreadyMemberErrorPage(UserAccount user, OrganizationMembership pendingOrganizationMembership, Organization organization, BrandInfo brandInfo) {
    UserAccount requester = accountRepository.getUserAccountById(pendingOrganizationMembership.getCreator_id());

    URI logoutPageUrl = uriInfo.getBaseUriBuilder().path(LogoutPage.class)
        .queryParam(BrandHelper.BRAND_PARAM, brandInfo.getBrand_id())
        .build();
    URI refuseFormAction = uriInfo.getBaseUriBuilder()
        .path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "refuseInvitation")
        .build(serializedToken);
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(
            OrgMembershipInvitationSoyInfo.MEMBERSHIP_INVITATION_ALREADY_MEMBER_ERROR,
            user.getLocale(),
            ImmutableMap.<String, String>builderWithExpectedSize(6)
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.LOGOUT_PAGE_URL, logoutPageUrl.toString())
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString())
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.ORGANIZATION_NAME, organization.getName())
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName())
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.INVITED_EMAIL, pendingOrganizationMembership.getEmail())
                .put(MembershipInvitationAlreadyMemberErrorSoyTemplateInfo.CURRENT_USER, MoreObjects.firstNonNull(user.getEmail_address(), user.getDisplayName()))
                .build(),
            brandInfo
        ))
        .build();
  }

  private void notifyAdmins(Organization organization, String invitedUserEmail, UserAccount requester, boolean acceptedInvitation) {
    ImmutableMap<String, String> data;
    final SoyTemplateInfo templateInfo;
    if (acceptedInvitation) {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.ACCEPTED_MEMBERSHIP_INVITATION_ADMIN_MESSAGE;
      data = ImmutableMap.of(
          AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName(),
          AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName()
      );
    } else {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.REJECTED_MEMBERSHIP_INVITATION_ADMIN_MESSAGE;
      data = ImmutableMap.of(
          RejectedMembershipInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          RejectedMembershipInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName(),
          RejectedMembershipInvitationAdminMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName()
      );
    }

    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(templateInfo, locale,
          SanitizedContent.ContentKind.TEXT, data)));
    }

    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organization.getId());
    for (OrganizationMembership admin : admins) {
      if (admin.getAccountId().equals(requester.getId())) {
        continue;
      }

      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin.getAccountId());
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admin {} for accepted or refused organization membership invitation.", admin.getAccountId(), e);
      }
    }
  }

  private void notifyRequester(Organization organization, String invitedUserEmail, String requesterId, boolean acceptedInvitation) {
    ImmutableMap<String, String> data;
    final SoyTemplateInfo templateInfo;
    if (acceptedInvitation) {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.ACCEPTED_MEMBERSHIP_INVITATION_REQUESTER_MESSAGE;
      data = ImmutableMap.of(
          AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName()
      );
    } else {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.REJECTED_MEMBERSHIP_INVITATION_REQUESTER_MESSAGE;
      data = ImmutableMap.of(
          RejectedMembershipInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          RejectedMembershipInvitationRequesterMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName()
      );
    }

    Notification notification = new Notification();
    notification.setTime(Instant.now());
    notification.setStatus(Notification.Status.UNREAD);
    notification.setUser_id(requesterId);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(templateInfo, locale,
          SanitizedContent.ContentKind.TEXT, data)));
    }

    try {
      notificationRepository.createNotification(notification);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying requester {} for accepted or refused organization membership invitation.", requesterId, e);
    }
  }
}
