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
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

import oasis.model.DuplicateKeyException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AppInstanceInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.AppAdminHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationSoyInfo;
import oasis.soy.templates.AppInstanceInvitationSoyInfo.AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationSoyInfo.AppInstanceInvitationSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.LogoutPage;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/apps/invitation/{token}")
@Produces(MediaType.TEXT_HTML)
@Authenticated @User
public class AppInstanceInvitationPage {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceInvitationPage.class);

  @PathParam("token") String serializedToken;

  @Inject AccountRepository accountRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject TokenRepository tokenRepository;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;
  @Inject Urls urls;
  @Inject AppAdminHelper appAdminHelper;
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

    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return generateNotFoundPage(userLocale, brandInfo);
    }

    AccessControlEntry pendingAccessControlEntry = accessControlRepository.getPendingAccessControlEntry(
        appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      // The token is not related to a pending ACE so it is useless
      // Let's remove it
      tokenRepository.revokeToken(appInstanceInvitationToken.getId());
      return generateNotFoundPage(userLocale, brandInfo);
    }

    AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
    if (appInstance == null) {
      return generateNotFoundPage(userLocale, brandInfo);
    }

    if (accessControlRepository.getAccessControlEntry(appInstance.getId(), userId) != null) {
      return generateAlreadyUserErrorPage(user, pendingAccessControlEntry, appInstance, brandInfo);
    }

    // XXX: if app-instance is in STOPPED status, we allow the user to proceed, just in case it's later moved back to RUNNING
    // app-instance should not be in PENDING state if we reach this code.
    return generatePage(userLocale, pendingAccessControlEntry, appInstance, brandInfo);
  }

  @POST
  @StrictReferer
  @Path("/accept")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response acceptInvitation() {
    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return goBackToFirstStep();
    }

    String currentAccountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    AccessControlEntry pendingAccessControlEntry = accessControlRepository.getPendingAccessControlEntry(
        appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      return goBackToFirstStep();
    }
    AccessControlEntry entry;
    try {
      entry = accessControlRepository
          .acceptPendingAccessControlEntry(appInstanceInvitationToken.getAceId(), currentAccountId);
    } catch (DuplicateKeyException e) {
      return goBackToFirstStep();
    }
    if (entry == null) {
      return goBackToFirstStep();
    }

    try {
      AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
      if (appInstance.getStatus() == AppInstance.InstantiationStatus.RUNNING) {
        UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());
        notifyRequester(appInstance, pendingAccessControlEntry.getEmail(), requester.getId(), true);
        notifyAdmins(appInstance, pendingAccessControlEntry.getEmail(), requester, true);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted app-instance invitation.", e);
    }

    tokenRepository.revokeToken(appInstanceInvitationToken.getId());

    return Response.seeOther(
        urls.myNetwork().orElse(uriInfo.getBaseUri())
    ).build();
  }

  @POST
  @StrictReferer
  @Path("/refuse")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response refuseInvitation() {
    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return goBackToFirstStep();
    }

    AccessControlEntry pendingAccessControlEntry = accessControlRepository
        .getPendingAccessControlEntry(appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      return goBackToFirstStep();
    }

    boolean deleted = accessControlRepository.deletePendingAccessControlEntry(appInstanceInvitationToken.getAceId());
    if (!deleted) {
      return goBackToFirstStep();
    }

    try {
      AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
      if (appInstance.getStatus() == AppInstance.InstantiationStatus.RUNNING) {
        UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());
        notifyRequester(appInstance, pendingAccessControlEntry.getEmail(), requester.getId(), false);
        notifyAdmins(appInstance, pendingAccessControlEntry.getEmail(), requester, false);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted app-instance invitation.", e);
    }

    tokenRepository.revokeToken(appInstanceInvitationToken.getId());

    return Response.seeOther(
        urls.myNetwork().orElse(uriInfo.getBaseUri())
    ).build();
  }

  private Response generatePage(ULocale locale, AccessControlEntry pendingAccessControlEntry, AppInstance appInstance, BrandInfo brandInfo) {
    UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());

    URI acceptFormAction = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "acceptInvitation")
        .build(serializedToken);
    URI refuseFormAction = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "refuseInvitation")
        .build(serializedToken);
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(
            AppInstanceInvitationSoyInfo.APP_INSTANCE_INVITATION,
            locale,
            ImmutableMap.of(
                AppInstanceInvitationSoyTemplateInfo.ACCEPT_FORM_ACTION, acceptFormAction.toString(),
                AppInstanceInvitationSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString(),
                AppInstanceInvitationSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale),
                AppInstanceInvitationSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName(),
                AppInstanceInvitationSoyTemplateInfo.INVITED_EMAIL, pendingAccessControlEntry.getEmail()
            ),
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
        .entity(new SoyTemplate(AppInstanceInvitationSoyInfo.APP_INSTANCE_INVITATION_TOKEN_ERROR, locale, null, brandInfo))
        .build();
  }

  private Response goBackToFirstStep() {
    // The token was expired between showInvitation page loading and form action
    // So let's restart the process by sending the user to the showInvitation page which should display an error
    URI showInvitationUri = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "showInvitation")
        .build(serializedToken);
    return Response.seeOther(showInvitationUri).build();
  }

  private Response generateAlreadyUserErrorPage(UserAccount user, AccessControlEntry pendingAccessControlEntry, AppInstance appInstance, BrandInfo brandInfo) {
    UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());

    URI logoutPageUrl = uriInfo.getBaseUriBuilder().path(LogoutPage.class).build();
    URI refuseFormAction = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "refuseInvitation")
        .build(serializedToken);
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(
            AppInstanceInvitationSoyInfo.APP_INSTANCE_INVITATION_ALREADY_USER_ERROR,
            user.getLocale(),
            ImmutableMap.<String, String>builder()
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.LOGOUT_PAGE_URL, logoutPageUrl.toString())
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString())
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(user.getLocale()))
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName())
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.INVITED_EMAIL, pendingAccessControlEntry.getEmail())
                .put(AppInstanceInvitationAlreadyUserErrorSoyTemplateInfo.CURRENT_USER, MoreObjects.firstNonNull(user.getEmail_address(), user.getDisplayName())
            ).build(),
            brandInfo
        ))
        .build();
  }

  private void notifyAdmins(AppInstance appInstance, String invitedUserEmail, UserAccount requester, boolean acceptedInvitation) {
    final String requesterName = requester.getDisplayName();
    final SoyTemplateInfo templateInfo;
    final Function<ULocale, ImmutableMap<String, ?>> dataProvider;
    if (acceptedInvitation) {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.ACCEPTED_APP_INSTANCE_INVITATION_ADMIN_MESSAGE;
      dataProvider = locale -> ImmutableMap.of(
          AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requesterName,
          AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale)
      );
    } else {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.REJECTED_APP_INSTANCE_INVITATION_ADMIN_MESSAGE;
      dataProvider = locale -> ImmutableMap.of(
          RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requesterName,
          RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale)
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
          SanitizedContent.ContentKind.TEXT, dataProvider.apply(locale))));
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

  private void notifyRequester(AppInstance appInstance, String invitedUserEmail, String requesterId, boolean acceptedInvitation) {
    final SoyTemplateInfo templateInfo;
    final Function<ULocale, ImmutableMap<String, String>> dataProvider;
    if (acceptedInvitation) {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.ACCEPTED_APP_INSTANCE_INVITATION_REQUESTER_MESSAGE;
      dataProvider = locale -> ImmutableMap.of(
          AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale)
      );
    } else {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.REJECTED_APP_INSTANCE_INVITATION_REQUESTER_MESSAGE;
      dataProvider = locale -> ImmutableMap.of(
          RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail,
          RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale)
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
          SanitizedContent.ContentKind.TEXT, dataProvider.apply(locale))));
    }

    try {
      notificationRepository.createNotification(notification);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying requester {} for accepted or refused app-instance invitation.", requesterId, e);
    }
  }
}
