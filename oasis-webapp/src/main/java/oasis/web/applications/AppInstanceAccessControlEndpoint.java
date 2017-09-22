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
package oasis.web.applications;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.authn.AppInstanceInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.AppInstanceInvitationMailSoyInfo;
import oasis.soy.templates.AppInstanceInvitationMailSoyInfo.NewAppInstanceInvitationBodySoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationMailSoyInfo.NewAppInstanceInvitationSubjectSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.NewAppInstanceInvitationAdminMessageSoyTemplateInfo;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.WithScopes;
import oasis.web.i18n.LocaleHelper;
import oasis.web.utils.ResponseFactory;

@Path("/apps/acl/instance/{instance_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class AppInstanceAccessControlEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceAccessControlEndpoint.class);

  @Inject AccessControlRepository accessControlRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AccountRepository accountRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject EtagService etagService;
  @Inject MailSender mailSender;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;
  @Inject TokenRepository tokenRepository;
  @Inject NotificationRepository notificationRepository;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("instance_id") String instance_id;

  @GET
  public Response get() {
    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }

    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!instance_id.equals(accessToken.getServiceProviderId()) && !ClientIds.PORTAL.equals(accessToken.getServiceProviderId())) {
      return ResponseFactory.forbidden("Cannot read the access control list of another instance");
    }
    if (!appAdminHelper.isAdmin(accessToken.getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
    }

    Map<String, ACE> acesByUser = new HashMap<>();
    // First add all app_users
    for (AccessControlEntry input : accessControlRepository.getAccessControlListForAppInstance(instance_id)) {
      if (input.getStatus() == AccessControlEntry.Status.PENDING) {
        continue; // That shouldn't happen, but just in case
      }
      assert !acesByUser.containsKey(input.getUser_id());
      ACE ace = new ACE();
      // Note: the *_name fields will be filled later
      ace.id = input.getId();
      ace.entry_uri = uriInfo.getBaseUriBuilder().path(AccessControlEntryEndpoint.class).build(input.getId()).toString();
      ace.entry_etag = etagService.getEtag(input).toString();
      ace.instance_id = input.getInstance_id();
      ace.user_id = input.getUser_id();
      ace.app_user = true;
      ace.creator_id = input.getCreator_id();
      ace.created = input.getCreated();
      ace.accepted = input.getAccepted();
      acesByUser.put(ace.user_id, ace);
    }
    // Then update the app_admin flag or insert a new ACE
    appAdminHelper.getAdmins(instance).forEach(app_admin -> {
      ACE ace = acesByUser.get(app_admin);
      if (ace == null) {
        ace = new ACE();
        acesByUser.put(app_admin, ace);
        // XXX: no id or creator_id.
        ace.instance_id = instance_id;
        ace.user_id = app_admin;
      }
      ace.app_admin = true;
    });
    // Finally, compute the *_name fields for all entries
    // Use a cache as we're likely to see the same user several times
    Map<String, UserAccount> accountsById = new HashMap<>();
    final UserAccount sentinel = new UserAccount();
    for (ACE ace : acesByUser.values()) {
      UserAccount user = accountsById.get(ace.user_id);
      if (user == null) {
        user = accountRepository.getUserAccountById(ace.user_id);
        accountsById.put(ace.user_id, user == null ? sentinel : user);
      }
      ace.user_name = user == null ? null : user.getDisplayName();
      ace.user_email_address = user == null ? null : user.getEmail_address();

      UserAccount creator = accountsById.get(ace.creator_id);
      if (creator == null) {
        creator = accountRepository.getUserAccountById(ace.creator_id);
        accountsById.put(ace.user_id, creator == null ? sentinel : creator);
      }
      ace.creator_name = creator == null ? null : creator.getDisplayName();
    }

    return Response.ok()
        .entity(new GenericEntity<Iterable<ACE>>(acesByUser.values()) {})
        .build();
  }

  @POST
  @WithScopes(Scopes.PORTAL)
  public Response addToList(AccessControlEntry ace) {
    if (ace.getInstance_id() != null && !instance_id.equals(ace.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }
    ace.setInstance_id(instance_id);
    // TODO: check that the user exists
    if (Strings.isNullOrEmpty(ace.getUser_id()) && Strings.isNullOrEmpty(ace.getEmail())) {
      return ResponseFactory.unprocessableEntity("user_id or email is missing");
    }
    if (!Strings.isNullOrEmpty(ace.getUser_id()) && !Strings.isNullOrEmpty(ace.getEmail())) {
      return ResponseFactory.unprocessableEntity("either user_id or email should be filled, but not both");
    }
    ace.setStatus(Strings.isNullOrEmpty(ace.getUser_id())
        ? AccessControlEntry.Status.PENDING
        // Adding members by ID (from the user graph) requires no invitation
        : AccessControlEntry.Status.ACCEPTED);

    AppInstance instance = appInstanceRepository.getAppInstance(instance_id);
    if (instance == null) {
      return ResponseFactory.NOT_FOUND;
    }
    String currentUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!appAdminHelper.isAdmin(currentUserId, instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the application instance");
    }
    ace.setCreator_id(currentUserId);

    ace = accessControlRepository.createAccessControlEntry(ace);
    if (ace == null) {
      return ResponseFactory.conflict("Entry for that user already exists");
    }

    if (ace.getStatus() == AccessControlEntry.Status.PENDING) {

      String pass = tokenHandler.generateRandom();
      AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.createAppInstanceInvitationToken(ace.getId(), pass);

      UserAccount requester = accountRepository.getUserAccountById(currentUserId);
      try {
        notifyUserForNewAppInstanceInvitation(ace.getEmail(), instance, requester, appInstanceInvitationToken, pass);
      } catch (Exception e) {
        logger.error("Error notifying a user about an invitation for using an app-instance", e);
        // The user needs to be notified or he may never accept the invitation
        accessControlRepository.deletePendingAccessControlEntry(ace.getId());
        tokenRepository.revokeToken(appInstanceInvitationToken.getId());
        return Response.serverError()
            .entity("Error while sending the invitation to the user")
            .build();
      }
      try {
        notifyAdminsForNewAppInstanceInvitation(ace.getEmail(), instance, requester);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app_admins after the requester {} invited a user", requester, e);
      }
    }

    URI aceUri = uriInfo.getBaseUriBuilder().path(AccessControlEntryEndpoint.class).build(ace.getId());
    return Response.created(aceUri)
        .contentLocation(aceUri)
        .tag(etagService.getEtag(ace))
        .entity(ace)
        .build();
  }

  private void notifyUserForNewAppInstanceInvitation(String invitedUserEmail, AppInstance appInstance, UserAccount requester,
      AppInstanceInvitationToken appInstanceInvitationToken, String tokenPass) {
    SoyMapData data = new SoyMapData();
    // Subject data
    data.put(NewAppInstanceInvitationSubjectSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(requester.getLocale()));
    data.put(NewAppInstanceInvitationSubjectSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
    // Body data
    URI uri = uriInfo.getBaseUriBuilder().path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "showInvitation")
        .build(TokenSerializer.serialize(appInstanceInvitationToken, tokenPass));
    data.put(NewAppInstanceInvitationBodySoyTemplateInfo.APP_INSTANCE_INVITATION_URL, uri.toString());
    data.put(NewAppInstanceInvitationBodySoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(requester.getLocale()));
    data.put(NewAppInstanceInvitationBodySoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());

    try {
      mailSender.send(new MailMessage()
          .setRecipient(invitedUserEmail, null)
          .setLocale(requester.getLocale())
          .setSubject(AppInstanceInvitationMailSoyInfo.NEW_APP_INSTANCE_INVITATION_SUBJECT)
          .setBody(AppInstanceInvitationMailSoyInfo.NEW_APP_INSTANCE_INVITATION_BODY)
          .setData(data)
          .setHtml());
    } catch (MessagingException e) {
      logger.error("Error while sending new app-instance invitation email to the invited user", e);
      throw new ServerErrorException(Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  private void notifyAdminsForNewAppInstanceInvitation(String invitedUserEmail, AppInstance appInstance, UserAccount requester) {
    SoyMapData data = new SoyMapData();
    data.put(NewAppInstanceInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
    data.put(NewAppInstanceInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());

    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      data.put(NewAppInstanceInvitationAdminMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale));
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          AppInstanceInvitationNotificationSoyInfo.ACCEPTED_APP_INSTANCE_INVITATION_ADMIN_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
    }

    appAdminHelper.getAdmins(appInstance)
        .filter(Predicate.isEqual(requester.getId()).negate())
        .forEach(admin -> {
          try {
            Notification notification = new Notification(notificationPrototype);
            notification.setUser_id(admin);
            notificationRepository.createNotification(notification);
          } catch (Exception e) {
            // Don't fail if we can't notify
            logger.error("Error notifying admin {} for accepted or refused app-instance invitation.", admin, e);
          }
        });
  }

  static class ACE {
    @JsonProperty String id;
    @JsonProperty String entry_uri;
    @JsonProperty String entry_etag;
    @JsonProperty String instance_id;
    @JsonProperty String user_id;
    @JsonProperty String user_name;
    // FIXME: This is temporary! We must not leak the user's email address without a prior agreement.
    @JsonProperty String user_email_address;
    @JsonProperty Instant created;
    @JsonProperty Instant accepted;
    @JsonProperty String creator_id;
    @JsonProperty String creator_name;
    @JsonProperty Boolean app_user;
    @JsonProperty Boolean app_admin;
  }
}
