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

import static oasis.soy.templates.DeletedOrganizationMembershipSoyInfo.DeletedMembershipMessageSoyTemplateInfo;

import java.time.Instant;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authz.Scopes;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.etag.EtagService;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.DeletedOrganizationMembershipSoyInfo;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.i18n.LocaleHelper;
import oasis.web.utils.ResponseFactory;

@Path("/d/memberships/membership/{membership_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
public class MembershipEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(MembershipEndpoint.class);

  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;
  @Inject SoyTemplateRenderer templateRenderer;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("membership_id") String membershipId;

  @GET
  public Response get() {
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(membershipId);
    if (membership == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!userId.equals(membership.getAccountId()) || !isOrgAdmin(userId, membership.getOrganizationId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    return Response.ok()
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  @DELETE
  public Response delete(@HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      ResponseFactory.preconditionRequiredIfMatch();
    }

    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(membershipId);
    if (membership == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!userId.equals(membership.getAccountId()) && !isOrgAdmin(userId, membership.getOrganizationId())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    boolean deleted;
    try {
      deleted = organizationMembershipRepository.deleteOrganizationMembership(membershipId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (!deleted) {
      return ResponseFactory.NOT_FOUND;
    }

    // Only create the notification when the targeted user is not the administrator
    if (userId.equals(membership.getAccountId())) {
      try {
        notifyAdminsOfDeletedMembership(membership.getOrganizationId(), membership.getAccountId(), membership.isAdmin());
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admins after the user (accountId = {}) has left the organization {}",
            membership.getAccountId(), membership.getOrganizationId(), e);
      }
    }

    return ResponseFactory.NO_CONTENT;
  }

  @PUT
  public Response put(
      @HeaderParam(HttpHeaders.IF_MATCH) List<EntityTag> ifMatch,
      MembershipRequest request) {
    if (ifMatch == null || ifMatch.isEmpty()) {
      ResponseFactory.preconditionRequiredIfMatch();
    }

    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(membershipId);
    if (membership == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (userId.equals(membership.getAccountId()) || !isOrgAdmin(userId, membership.getOrganizationId())) {
      // You must be an org admin, and can't update your own membership
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    membership.setAdmin(request.admin);
    try {
      membership = organizationMembershipRepository.updateOrganizationMembership(membership, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (membership == null) {
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok()
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  private boolean isOrgAdmin(String userId, String organizationId) {
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(userId, organizationId);
    return membership != null && membership.isAdmin();
  }

  private void notifyAdminsOfDeletedMembership(String organizationId, String userId, boolean isAdmin) {
    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organizationId);
    Organization organization = directoryRepository.getOrganization(organizationId);
    UserAccount userAccount = accountRepository.getUserAccountById(userId);

    Notification notificationPrototype = new Notification();
    SoyMapData data = new SoyMapData();
    data.put(DeletedMembershipMessageSoyTemplateInfo.USER_NAME, userAccount.getDisplayName());
    data.put(DeletedMembershipMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    data.put(DeletedMembershipMessageSoyTemplateInfo.IS_ADMIN, isAdmin);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          DeletedOrganizationMembershipSoyInfo.DELETED_MEMBERSHIP_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
    }
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);

    for (OrganizationMembership admin : admins) {
      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin.getAccountId());
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admin {} after the user (accountId = {}) has left the organization {}",
            admin.getId(), userAccount.getId(), organizationId, e);
      }
    }
  }

  static class MembershipRequest {
    @JsonProperty boolean admin;
  }
}
