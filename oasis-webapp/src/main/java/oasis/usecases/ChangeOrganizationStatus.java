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
package oasis.usecases;

import java.net.URI;
import java.time.Instant;

import javax.inject.Inject;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.ChangedOrganizationStatusSoyInfo;
import oasis.soy.templates.ChangedOrganizationStatusSoyInfo.RestoredOrganizationMessageSoyTemplateInfo;
import oasis.soy.templates.ChangedOrganizationStatusSoyInfo.SoftlyDeletedOrganizationMessageForAdminsSoyTemplateInfo;
import oasis.soy.templates.ChangedOrganizationStatusSoyInfo.SoftlyDeletedOrganizationMessageForRequesterSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.i18n.LocaleHelper;

@Value.Enclosing
public class ChangeOrganizationStatus {
  private static final Logger logger = LoggerFactory.getLogger(ChangeOrganizationStatus.class);

  @Inject AccountRepository accountRepository;
  @Inject ApplicationRepository applicationRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject Urls urls;

  public Response updateStatus(Request request) {
    ImmutableChangeOrganizationStatus.Response.Builder responseBuilder = ImmutableChangeOrganizationStatus.Response.builder();

    // Do nothing if the requested status is the same than the actual one
    if (request.organization().getStatus() == request.newStatus()) {
      return responseBuilder
          .organization(request.organization())
          .responseStatus(ResponseStatus.NOTHING_TO_MODIFY)
          .build();
    }

    if (applicationRepository.getCountByProvider(request.organization().getId()) > 0) {
      return responseBuilder
          .organization(request.organization())
          .responseStatus(ResponseStatus.ORGANIZATION_PROVIDES_APPLICATIONS)
          .build();
    }
    if (appInstanceRepository.getNonStoppedCountByOrganizationId(request.organization().getId()) > 0) {
      return responseBuilder
          .organization(request.organization())
          .responseStatus(ResponseStatus.ORGANIZATION_HAS_APPLICATION_INSTANCES)
          .build();
    }

    Organization updatedOrganization;
    try {
      updatedOrganization = directoryRepository.changeOrganizationStatus(request.organization().getId(), request.newStatus(), request.requesterId(), request.ifMatch());
    } catch (InvalidVersionException e) {
      return responseBuilder
          .organization(request.organization())
          .responseStatus(ResponseStatus.VERSION_CONFLICT)
          .build();
    }

    try {
      notifyOrganizationAdmins(request.requesterId(), updatedOrganization, request.newStatus());
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying app users after the user {} has stopped the instance {}", request.requesterId(),
          updatedOrganization.getName(), e);
    }

    return responseBuilder
        .organization(updatedOrganization)
        .responseStatus(ResponseStatus.SUCCESS)
        .build();
  }

  private void notifyOrganizationAdmins(String requesterId, Organization organization, Organization.Status newStatus) {
    switch (newStatus) {
      case AVAILABLE:
        notifyOrganizationAdminsForRestoredOrganization(organization);
        break;
      case DELETED:
        notifyOrganizationAdminsForStoppedOrganization(requesterId, organization);
        notifyRequesterForStoppedOrganization(requesterId, organization);
        break;
      default:
        // noop
    }
  }

  private void notifyOrganizationAdminsForStoppedOrganization(String requesterId, Organization organization) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);
    notificationPrototype.getAction_uri().set(ULocale.ROOT, urls.myNetwork().map(URI::toString).orElse(null));

    UserAccount requester = accountRepository.getUserAccountById(requesterId);
    SoyMapData data = new SoyMapData();
    data.put(SoftlyDeletedOrganizationMessageForAdminsSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    data.put(SoftlyDeletedOrganizationMessageForAdminsSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedOrganizationStatusSoyInfo.SOFTLY_DELETED_ORGANIZATION_MESSAGE_FOR_ADMINS, locale, SanitizedContent.ContentKind.TEXT, data)));
      notificationPrototype.getAction_label().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedOrganizationStatusSoyInfo.SOFTLY_DELETED_ORGANIZATION_ACTION, locale, SanitizedContent.ContentKind.TEXT)));
    }

    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organization.getId());
    for (OrganizationMembership admin : admins) {
      if (admin.getAccountId().equals(requesterId)) {
        continue;
      }

      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin.getAccountId());
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app admin {} after the requester {} has stopped the instance {}", admin.getAccountId(), requesterId, organization.getName(), e);
      }
    }
  }

  private void notifyRequesterForStoppedOrganization(String requesterId, Organization organization) {
    Notification notification = new Notification();
    notification.setTime(Instant.now());
    notification.setStatus(Notification.Status.UNREAD);
    notification.setUser_id(requesterId);
    notification.getAction_uri().set(ULocale.ROOT, urls.myNetwork().map(URI::toString).orElse(null));

    SoyMapData data = new SoyMapData();
    data.put(SoftlyDeletedOrganizationMessageForRequesterSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedOrganizationStatusSoyInfo.SOFTLY_DELETED_ORGANIZATION_MESSAGE_FOR_REQUESTER, locale, SanitizedContent.ContentKind.TEXT, data)));
      notification.getAction_label().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedOrganizationStatusSoyInfo.SOFTLY_DELETED_ORGANIZATION_ACTION, locale, SanitizedContent.ContentKind.TEXT)));
    }

    try {
      notificationRepository.createNotification(notification);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying the requester {} after stopping the instance {}", requesterId, organization.getName(), e);
    }
  }

  private void notifyOrganizationAdminsForRestoredOrganization(Organization organization) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      SoyMapData data = new SoyMapData();
      data.put(RestoredOrganizationMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());

      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedOrganizationStatusSoyInfo.RESTORED_ORGANIZATION_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
    }

    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organization.getId());
    for (OrganizationMembership admin : admins) {
      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin.getAccountId());
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app user {} after restoring the instance {}", admin.getAccountId(), organization.getName(), e);
      }
    }
  }

  @Value.Immutable
  public static interface Request {
    String requesterId();

    Organization organization();

    long[] ifMatch();

    Organization.Status newStatus();
  }

  @Value.Immutable
  public static interface Response {
    Organization organization();

    ResponseStatus responseStatus();
  }

  public static enum ResponseStatus {
    NOTHING_TO_MODIFY,
    SUCCESS,
    VERSION_CONFLICT,
    ORGANIZATION_PROVIDES_APPLICATIONS,
    ORGANIZATION_HAS_APPLICATION_INSTANCES,
  }
}
