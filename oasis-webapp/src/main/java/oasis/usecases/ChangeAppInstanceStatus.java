package oasis.usecases;

import javax.inject.Inject;

import org.immutables.value.Value;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.ChangedAppInstanceStatusSoyInfo;
import oasis.soy.templates.ChangedAppInstanceStatusSoyInfo.RestoredAppInstanceMessageSoyTemplateInfo;
import oasis.soy.templates.ChangedAppInstanceStatusSoyInfo.StoppedAppInstanceMessageForAdminsSoyTemplateInfo;
import oasis.soy.templates.ChangedAppInstanceStatusSoyInfo.StoppedAppInstanceMessageForRequesterSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.i18n.LocaleHelper;

@Value.Nested
public class ChangeAppInstanceStatus {
  private static final Logger logger = LoggerFactory.getLogger(ChangeAppInstanceStatus.class);

  @Inject AccountRepository accountRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject TokenRepository tokenRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject Urls urls;

  public Response updateStatus(Request request) {
    ImmutableChangeAppInstanceStatus.Response.Builder responseBuilder = ImmutableChangeAppInstanceStatus.Response.builder();

    // Do nothing if the requested status is the same than the actual one
    if (request.appInstance().getStatus() == request.newStatus()) {
      return responseBuilder
          .appInstance(request.appInstance())
          .responseStatus(ResponseStatus.NOTHING_TO_MODIFY)
          .build();
    }

    final AppInstance instance;
    try {
      instance = appInstanceRepository.updateStatus(request.appInstance().getId(), request.newStatus(), request.requesterId(), request.ifMatch());
    } catch (InvalidVersionException e) {
      return responseBuilder.appInstance(request.appInstance())
          .responseStatus(ResponseStatus.VERSION_CONFLICT)
          .build();
    }
    if (instance == null) {
      // i.e. the instance was deleted or updated between first DB call and the updateStatus()
      return responseBuilder.appInstance(request.appInstance())
          .responseStatus(ResponseStatus.NOT_FOUND)
          .build();
    }

    Service.Status servicesStatus = Service.Status.forAppInstanceStatus(request.newStatus());
    serviceRepository.changeServicesStatusForInstance(instance.getId(), servicesStatus);

    tokenRepository.revokeTokensForClient(instance.getId());

    try {
      notifyAdmins(request.requesterId(), instance, request.newStatus());
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying app admins after the user {} has stopped the instance {}", request.requesterId(),
          instance.getName().get(ULocale.ROOT), e);
    }

    return responseBuilder.appInstance(instance)
        .responseStatus(ResponseStatus.SUCCESS)
        .build();
  }

  private void notifyAdmins(String requesterId, AppInstance instance, AppInstance.InstantiationStatus newStatus) {
    switch (newStatus) {
      case RUNNING:
        notifyAdminsForRunningInstance(requesterId, instance);
        break;
      case STOPPED:
        notifyAdminsForStoppedInstance(requesterId, instance);
        notifyRequesterForStoppedInstance(requesterId, instance);
        break;
      case PENDING:
      default:
        // XXX: Should be unreachable
        // noop
    }
  }

  private void notifyAdminsForRunningInstance(String requesterId, AppInstance instance) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);

    Iterable<String> adminIds = appAdminHelper.getAdmins(instance);
    for (String adminId : adminIds) {
      try {
        Notification notification = new Notification(notificationPrototype);
        for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
          SoyMapData data = new SoyMapData();
          data.put(RestoredAppInstanceMessageSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale));

          ULocale messageLocale = locale;
          if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
            messageLocale = ULocale.ROOT;
          }
          notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
              ChangedAppInstanceStatusSoyInfo.RESTORED_APP_INSTANCE_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
        }
        notification.setUser_id(adminId);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app admin {} after the user {} has stopped the instance {}", adminId, requesterId,
            instance.getName().get(ULocale.ROOT), e);
      }
    }
  }

  private void notifyAdminsForStoppedInstance(String requesterId, AppInstance instance) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);

    UserAccount requester = accountRepository.getUserAccountById(requesterId);
    SoyMapData data = new SoyMapData();
    data.put(StoppedAppInstanceMessageForAdminsSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }

      data.put(StoppedAppInstanceMessageForAdminsSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale));
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_MESSAGE_FOR_ADMINS, locale, SanitizedContent.ContentKind.TEXT, data)));
    }

    if (urls.myApps().isPresent()) {
      for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
        ULocale actionLocale = locale;
        if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
          actionLocale = ULocale.ROOT;
        }

        notificationPrototype.getAction_label().set(actionLocale, templateRenderer.renderAsString(new SoyTemplate(
            ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_ACTION, locale, SanitizedContent.ContentKind.TEXT)));
      }
      notificationPrototype.getAction_uri().set(ULocale.ROOT, urls.myApps().transform(Functions.toStringFunction()).orNull());
    }

    Iterable<String> adminIds = appAdminHelper.getAdmins(instance);
    for (String adminId : adminIds) {
      if (adminId.equals(requesterId)) {
        continue;
      }

      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(adminId);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app admin {} after the user {} has stopped the instance {}", adminId, requesterId,
            instance.getName().get(ULocale.ROOT), e);
      }
    }
  }

  private void notifyRequesterForStoppedInstance(String requesterId, AppInstance instance) {
    Notification notification = new Notification();
    notification.setTime(Instant.now());
    notification.setStatus(Notification.Status.UNREAD);
    notification.setUser_id(requesterId);

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }

      SoyMapData data = new SoyMapData();
      data.put(StoppedAppInstanceMessageForRequesterSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale));

      notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_MESSAGE_FOR_REQUESTER, locale, SanitizedContent.ContentKind.TEXT, data)));
      if (urls.myApps().isPresent()) {
        notification.getAction_label().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
            ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_ACTION, locale, SanitizedContent.ContentKind.TEXT)));
      }
    }
    if (urls.myApps().isPresent()) {
      notification.getAction_uri().set(ULocale.ROOT, urls.myApps().transform(Functions.toStringFunction()).orNull());
    }

    try {
      notificationRepository.createNotification(notification);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying requester {} after stopping the instance {}", requesterId, requesterId,
          instance.getName().get(ULocale.ROOT), e);
    }
  }

  @Value.Immutable
  public static interface Request {
    String requesterId();

    AppInstance appInstance();

    long[] ifMatch();

    AppInstance.InstantiationStatus newStatus();
  }

  @Value.Immutable
  public static interface Response {
    AppInstance appInstance();

    ResponseStatus responseStatus();
  }

  public static enum ResponseStatus {
    NOTHING_TO_MODIFY,
    SUCCESS,
    VERSION_CONFLICT,
    NOT_FOUND
  }
}
