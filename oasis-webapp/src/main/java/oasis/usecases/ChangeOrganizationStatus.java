package oasis.usecases;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.immutables.value.Value;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.base.MoreObjects;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
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

@Value.Nested
public class ChangeOrganizationStatus {
  private static final Logger logger = LoggerFactory.getLogger(ChangeOrganizationStatus.class);

  @Inject AccountRepository accountRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject ChangeAppInstanceStatus changeAppInstanceStatus;
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

    Organization updatedOrganization;
    try {
      updatedOrganization = directoryRepository.changeOrganizationStatus(request.organization().getId(), request.newStatus(), request.requesterId(), request.ifMatch());
    } catch (InvalidVersionException e) {
      return responseBuilder
          .organization(request.organization())
          .responseStatus(ResponseStatus.VERSION_CONFLICT)
          .build();
    }

    if (request.newStatus() == Organization.Status.DELETED) {
      // PENDING instances are not STOPPED directly.
      // They will be set to stopped when they're provisioned
      // So we only update RUNNING instances
      Iterable<AppInstance> appInstances = appInstanceRepository.findByOrganizationIdAndStatus(updatedOrganization.getId(), AppInstance.InstantiationStatus.RUNNING);
      List<AppInstance> stoppedInstances = new ArrayList<>();
      try {
        for (AppInstance appInstance : appInstances) {
          ChangeAppInstanceStatus.Response changeAppStatusResponse = stopInstance(appInstance, request.requesterId());

          switch (changeAppStatusResponse.responseStatus()) {
            case SUCCESS:
              stoppedInstances.add(changeAppStatusResponse.appInstance());
              break;
            case NOTHING_TO_MODIFY:
            case NOT_FOUND:
              break;
            case VERSION_CONFLICT:
              logger.error("Error while stopping app-instances.");
              updatedOrganization = rollbackProcess(request, stoppedInstances);
              return responseBuilder
                  .organization(updatedOrganization)
                  .responseStatus(ResponseStatus.CHANGE_APP_INSTANCE_STATUS_ERROR)
                  .build();
            default:
              throw new IllegalStateException();
          }
        }
      } catch (Exception e) {
        logger.error("Error while stopping app-instances.", e);
        updatedOrganization = rollbackProcess(request, stoppedInstances);
        return responseBuilder
            .organization(updatedOrganization)
            .responseStatus(ResponseStatus.CHANGE_APP_INSTANCE_STATUS_ERROR)
            .build();
      }
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

  private Organization rollbackProcess(Request request, List<AppInstance> stoppedInstances) {
    Organization updatedOrganization = request.organization();
    try {
      updatedOrganization = directoryRepository.changeOrganizationStatus(request.organization().getId(), request.newStatus(), request.requesterId());
    } catch (Exception e) {
      logger.error("Error while trying to rollback organization status.", e);
    }
    for (AppInstance stoppedInstance : stoppedInstances) {
      try {
        ImmutableChangeAppInstanceStatus.Request r = ImmutableChangeAppInstanceStatus.Request.builder()
            .appInstance(stoppedInstance)
            .newStatus(AppInstance.InstantiationStatus.RUNNING)
            .requesterId(MoreObjects.firstNonNull(stoppedInstance.getStatus_change_requester_id(), request.requesterId()))
            .notifyAdmins(false)
            .build();
        ChangeAppInstanceStatus.Response response = changeAppInstanceStatus.updateStatus(r);
        switch (response.responseStatus()) {
          case SUCCESS:
          case NOTHING_TO_MODIFY:
            break;
          case NOT_FOUND:
            logger.error("Error rolling app-instance {} back to running status.", stoppedInstance.getId());
            break;
          case VERSION_CONFLICT:
          default:
            throw new AssertionError();
        }
      } catch (Exception e) {
        logger.error("Error rolling app-instance {} back to running status.", stoppedInstance.getId(), e);
      }
    }
    return updatedOrganization;
  }

  private ChangeAppInstanceStatus.Response stopInstance(AppInstance appInstance, String requesterId) {
    ImmutableChangeAppInstanceStatus.Request request = ImmutableChangeAppInstanceStatus.Request.builder()
        .appInstance(appInstance)
        .newStatus(AppInstance.InstantiationStatus.STOPPED)
        .requesterId(requesterId)
        .notifyAdmins(false)
        .build();
    return changeAppInstanceStatus.updateStatus(request);
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
    notificationPrototype.getAction_uri().set(ULocale.ROOT, urls.myNetwork().transform(Functions.toStringFunction()).orNull());

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
    notification.getAction_uri().set(ULocale.ROOT, urls.myNetwork().transform(Functions.toStringFunction()).orNull());

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
    CHANGE_APP_INSTANCE_STATUS_ERROR
  }
}
