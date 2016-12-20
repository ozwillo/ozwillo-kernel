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

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.MembershipInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authn.TokenHandler;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.AcceptedMembershipInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.RejectedMembershipInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.RejectedMembershipInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationSoyInfo.MembershipInvitationSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
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
  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject TokenRepository tokenRepository;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;
  @Inject Urls urls;

  @Context Request request;
  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Path("")
  public Response showInvitation() {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount user = accountRepository.getUserAccountById(userId);
    ULocale userLocale = user.getLocale();

    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return generateNotFoundPage(userLocale);
    }

    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository.getPendingOrganizationMembership(
        membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      // The token is not related to a pending membership so it is useless
      // Let's remove it
      tokenRepository.revokeToken(membershipInvitationToken.getId());
      return generateNotFoundPage(userLocale);
    }

    Organization organization = directoryRepository.getOrganization(pendingOrganizationMembership.getOrganizationId());
    if (organization == null) {
      return generateNotFoundPage(userLocale);
    }

    // XXX: if organization is in DELETED status, we allow the user to proceed, just in case it's later moved back to AVAILABLE
    return generatePage(userLocale, pendingOrganizationMembership, organization);
  }

  @POST
  @StrictReferer
  @Path("/accept")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response acceptInvitation() {
    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return goBackToFirstStep();
    }
    tokenRepository.revokeToken(membershipInvitationToken.getId());

    String currentAccountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository.getPendingOrganizationMembership(
        membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      return goBackToFirstStep();
    }
    OrganizationMembership membership = organizationMembershipRepository
        .acceptPendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId(), currentAccountId);
    if (membership == null) {
      return goBackToFirstStep();
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

    if (urls.myNetwork().isPresent()) {
      return Response.seeOther(urls.myNetwork().get()).build();
    }
    return Response.seeOther(uriInfo.getBaseUri()).build();
  }

  @POST
  @StrictReferer
  @Path("/refuse")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response refuseInvitation() {
    MembershipInvitationToken membershipInvitationToken = tokenHandler.getCheckedToken(serializedToken, MembershipInvitationToken.class);
    if (membershipInvitationToken == null) {
      return goBackToFirstStep();
    }
    tokenRepository.revokeToken(membershipInvitationToken.getId());

    OrganizationMembership pendingOrganizationMembership = organizationMembershipRepository
        .getPendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId());
    if (pendingOrganizationMembership == null) {
      return goBackToFirstStep();
    }

    boolean deleted = organizationMembershipRepository.deletePendingOrganizationMembership(membershipInvitationToken.getOrganizationMembershipId());
    if (!deleted) {
      return goBackToFirstStep();
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

    if (urls.myNetwork().isPresent()) {
      return Response.seeOther(urls.myNetwork().get()).build();
    }
    return Response.seeOther(uriInfo.getBaseUri()).build();
  }

  private Response generatePage(ULocale locale, OrganizationMembership pendingOrganizationMembership, Organization organization) {
    UserAccount requester = accountRepository.getUserAccountById(pendingOrganizationMembership.getCreator_id());

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
            new SoyMapData(
                MembershipInvitationSoyTemplateInfo.ACCEPT_FORM_ACTION, acceptFormAction.toString(),
                MembershipInvitationSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString(),
                MembershipInvitationSoyTemplateInfo.ORGANIZATION_NAME, organization.getName(),
                MembershipInvitationSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName()
            )
        ))
        .build();
  }

  private Response generateNotFoundPage(ULocale locale) {
    return Response.status(Response.Status.NOT_FOUND)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(OrgMembershipInvitationSoyInfo.MEMBERSHIP_INVITATION_TOKEN_ERROR, locale))
        .build();
  }

  private Response goBackToFirstStep() {
    // The token was expired between showInvitation page loading and form action
    // So let's restart the process by sending the user to the showInvitation page which should display an error
    URI showInvitationUri = uriInfo.getBaseUriBuilder()
        .path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "showInvitation")
        .build(serializedToken);
    return Response.seeOther(showInvitationUri).build();
  }

  private void notifyAdmins(Organization organization, String invitedUserEmail, UserAccount requester, boolean acceptedInvitation) {
    SoyMapData data = new SoyMapData();
    final SoyTemplateInfo templateInfo;
    if (acceptedInvitation) {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.ACCEPTED_MEMBERSHIP_INVITATION_ADMIN_MESSAGE;
      data.put(AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
      data.put(AcceptedMembershipInvitationAdminMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    } else {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.REJECTED_MEMBERSHIP_INVITATION_ADMIN_MESSAGE;
      data.put(RejectedMembershipInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(RejectedMembershipInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
      data.put(RejectedMembershipInvitationAdminMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
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
    SoyMapData data = new SoyMapData();
    final SoyTemplateInfo templateInfo;
    if (acceptedInvitation) {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.ACCEPTED_MEMBERSHIP_INVITATION_REQUESTER_MESSAGE;
      data.put(AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(AcceptedMembershipInvitationRequesterMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    } else {
      templateInfo = OrgMembershipInvitationNotificationSoyInfo.REJECTED_MEMBERSHIP_INVITATION_REQUESTER_MESSAGE;
      data.put(RejectedMembershipInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(RejectedMembershipInvitationRequesterMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
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
