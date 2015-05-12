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
package oasis.web.userdirectory;

import java.net.URI;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.MembershipInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.Scopes;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.etag.EtagService;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.OrgMembershipInvitationMailSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationMailSoyInfo.NewMembershipInvitationBodySoyTemplateInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo;
import oasis.soy.templates.OrgMembershipInvitationNotificationSoyInfo.NewMembershipInvitationAdminMessageSoyTemplateInfo;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.i18n.LocaleHelper;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/d/memberships/org/{organization_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@WithScopes(Scopes.PORTAL)
@Api(value = "memberships-org", description = "Organization Memberships (from the organization point of view)")
public class OrganizationMembershipEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(OrganizationMembershipEndpoint.class);

  @Inject AccountRepository accountRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject TokenRepository tokenRepository;
  @Inject EtagService etagService;
  @Inject MailSender mailSender;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  @ApiOperation(
      value = "Retrieves users who are members of the organization",
      response = OrgMembership.class,
      responseContainer = "Array"
  )
  public Response get(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), organizationId);
    if (membership == null || !membership.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin for the organization");
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getMembersOfOrganization(organizationId, start, limit);
    return toResponse(memberships);
  }

  @GET
  @Path("/admins")
  public Response getAdmins(@QueryParam("start") int start, @QueryParam("limit") int limit) {
    OrganizationMembership membership = organizationMembershipRepository
        .getOrganizationMembership(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), organizationId);
    if (membership == null) {
      return ResponseFactory.forbidden("Current user is not a member of the organization");
    }
    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organizationId, start, limit);
    return toResponse(admins);
  }

  private Response toResponse(Iterable<OrganizationMembership> memberships) {
    return Response.ok()
        .entity(new GenericEntity<Iterable<OrgMembership>>(Iterables.transform(memberships,
            new Function<OrganizationMembership, OrgMembership>() {
              @Override
              public OrgMembership apply(OrganizationMembership input) {
                OrgMembership membership = new OrgMembership();
                membership.id = input.getId();
                membership.membership_uri = Resteasy1099.getBaseUriBuilder(uriInfo).path(MembershipEndpoint.class).build(input.getId()).toString();
                membership.membership_etag = etagService.getEtag(input);
                membership.account_id = input.getAccountId();
                // TODO: check access rights to the user name
                final UserAccount account = accountRepository.getUserAccountById(input.getAccountId());
                membership.account_name = account == null ? null : account.getDisplayName();
                membership.admin = input.isAdmin();
                return membership;
              }
            })) {})
        .build();
  }

  @POST
  @ApiOperation(
      value = "Creates a pending organization membership",
      response = OrganizationMembership.class
  )
  public Response post(MembershipRequest request) {
    String requesterId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership ownerMembership = organizationMembershipRepository.getOrganizationMembership(requesterId, organizationId);
    if (ownerMembership == null || !ownerMembership.isAdmin()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    Organization organization = directoryRepository.getOrganization(organizationId);
    if (organization == null) {
      return ResponseFactory.NOT_FOUND;
    }

    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrganizationId(organizationId);
    membership.setEmail(request.email);
    membership.setAdmin(false);
    membership.setStatus(OrganizationMembership.Status.PENDING);
    membership.setCreated(Instant.now());
    membership.setCreator_id(requesterId);
    membership = organizationMembershipRepository.createPendingOrganizationMembership(membership);

    if (membership == null) {
      return Response.status(Response.Status.CONFLICT).build();
    }

    String pass = tokenHandler.generateRandom();
    MembershipInvitationToken membershipInvitationToken = tokenHandler.createInvitationToken(membership.getId(), pass);

    UserAccount requester = accountRepository.getUserAccountById(requesterId);
    try {
      notifyUserForNewMembershipInvitation(request.email, organization, requester, membershipInvitationToken, pass);
    } catch (Exception e) {
      logger.error("Error notifying a user about an invitation for joining an organization", e);
      // The user needs to be notified or he may never accept the invitation
      organizationMembershipRepository.deletePendingOrganizationMembership(membership.getId());
      tokenRepository.revokeToken(membershipInvitationToken.getId());
      return Response.serverError()
          .entity("Error while sending the invitation to the user")
          .build();
    }
    try {
      notifyAdminsForNewMembershipInvitation(request.email, organization, requester);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying organization admins after the requester {} invited a user");
    }

    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(MembershipEndpoint.class).build(membership.getId()))
        .tag(etagService.getEtag(membership))
        .entity(membership)
        .build();
  }

  private void notifyUserForNewMembershipInvitation(String invitedUserEmail, Organization organization, UserAccount requester,
      MembershipInvitationToken membershipInvitationToken, String tokenPass) {
    SoyMapData data = new SoyMapData();
    // Subject data
    data.put(OrgMembershipInvitationMailSoyInfo.NewMembershipInvitationSubjectSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    // Body data
    URI uri = uriInfo.getBaseUriBuilder().path(MembershipInvitationPage.class)
        .path(MembershipInvitationPage.class, "showInvitation")
        .build(TokenSerializer.serialize(membershipInvitationToken, tokenPass));
    data.put(NewMembershipInvitationBodySoyTemplateInfo.MEMBERSHIP_INVITATION_URL, uri.toString());
    data.put(NewMembershipInvitationBodySoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    data.put(NewMembershipInvitationBodySoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());

    try {
      mailSender.send(new MailMessage()
          .setRecipient(invitedUserEmail, null)
          .setLocale(requester.getLocale())
          .setSubject(OrgMembershipInvitationMailSoyInfo.NEW_MEMBERSHIP_INVITATION_SUBJECT)
          .setBody(OrgMembershipInvitationMailSoyInfo.NEW_MEMBERSHIP_INVITATION_BODY)
          .setData(data)
          .setHtml());
    } catch (MessagingException e) {
      logger.error("Error while sending new membership invitation email to the invited user", e);
      throw new ServerErrorException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  private void notifyAdminsForNewMembershipInvitation(String invitedUserEmail, Organization organization, UserAccount requester) {
    SoyMapData data = new SoyMapData();
    data.put(NewMembershipInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
    data.put(NewMembershipInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
    data.put(NewMembershipInvitationAdminMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());

    Notification notificationPrototype = new Notification();
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          OrgMembershipInvitationNotificationSoyInfo.ACCEPTED_MEMBERSHIP_INVITATION_ADMIN_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
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

  static class OrgMembership {
    @JsonProperty String id;
    @JsonProperty String membership_uri;
    @JsonProperty String membership_etag;
    @JsonProperty String account_id;
    @JsonProperty String account_name;
    @JsonProperty boolean admin;
  }

  static class MembershipRequest {
    @JsonProperty String email;
  }
}
