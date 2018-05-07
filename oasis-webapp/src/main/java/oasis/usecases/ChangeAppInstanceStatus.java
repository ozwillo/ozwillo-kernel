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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent;
import com.ibm.icu.util.ULocale;

import net.ltgt.jaxrs.webhook.client.WebhookSignatureFilter;
import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
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

@Value.Enclosing
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
  @Inject Provider<Client> clientProvider;

  @SuppressWarnings("FutureReturnValueIgnored")
  public Response updateStatus(final Request request) {
    ImmutableChangeAppInstanceStatus.Response.Builder responseBuilder = ImmutableChangeAppInstanceStatus.Response.builder();

    // Do nothing if the requested status is the same than the actual one
    if (request.appInstance().getStatus() == request.newStatus()) {
      return responseBuilder
          .appInstance(request.appInstance())
          .responseStatus(ResponseStatus.NOTHING_TO_MODIFY)
          .build();
    }

    final AppInstance instance;
    if (request.ifMatch() != null) {
      try {
        instance = appInstanceRepository.updateStatus(request.appInstance().getId(), request.newStatus(), request.requesterId(), request.ifMatch());
      } catch (InvalidVersionException e) {
        return responseBuilder.appInstance(request.appInstance())
            .responseStatus(ResponseStatus.VERSION_CONFLICT)
            .build();
      }
    } else {
      instance = appInstanceRepository.updateStatus(request.appInstance().getId(), request.newStatus(), request.requesterId());
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

    if (request.notifyAdmins()) {
      try {
        notifyAdmins(request.requesterId(), instance, request.newStatus());
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app admins after the user {} has stopped the instance {}", request.requesterId(),
            instance.getName().get(ULocale.ROOT), e);
      }
    }

    // XXX: Call provider depending on a request parameter?
    if (!Strings.isNullOrEmpty(instance.getStatus_changed_uri())) {
      ImmutableChangeAppInstanceStatus.ProviderRequest providerRequest = ImmutableChangeAppInstanceStatus.ProviderRequest.builder()
          .instance_id(instance.getId())
          .status(request.newStatus())
          .build();
      // It doesn't matter if this request has failed or not, the provider is just notified
      // He is the only one who should care about the final result
      clientProvider.get()
          .target(instance.getStatus_changed_uri())
          .register(new WebhookSignatureFilter(instance.getStatus_changed_secret()))
          .request()
          .async()
          .post(Entity.json(providerRequest), new InvocationCallback<javax.ws.rs.core.Response>() {
            @Override
            public void completed(javax.ws.rs.core.Response response) {
              try {
                logger.trace("App-instance {} notified of status changed to {}.", instance.getId(), request.newStatus());
              } finally {
                response.close();
              }
            }

            @Override
            public void failed(Throwable throwable) {
              logger.error("Error when notifying app-instance {} of status changed to {}", instance.getId(), request.newStatus(), throwable);
            }
          });
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

    appAdminHelper.getAdmins(instance).forEach(adminId -> {
      try {
        Notification notification = new Notification(notificationPrototype);
        for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {

          ULocale messageLocale = locale;
          if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
            messageLocale = ULocale.ROOT;
          }
          notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
              ChangedAppInstanceStatusSoyInfo.RESTORED_APP_INSTANCE_MESSAGE, locale, SanitizedContent.ContentKind.TEXT,
              ImmutableMap.of(
                  RestoredAppInstanceMessageSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale)
              ))));
        }
        notification.setUser_id(adminId);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying app admin {} after the user {} has stopped the instance {}", adminId, requesterId,
            instance.getName().get(ULocale.ROOT), e);
      }
    });
  }

  private void notifyAdminsForStoppedInstance(String requesterId, AppInstance instance) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);

    final String requesterName = accountRepository.getUserAccountById(requesterId).getDisplayName();

    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }

      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_MESSAGE_FOR_ADMINS, locale, SanitizedContent.ContentKind.TEXT,
          ImmutableMap.of(
              StoppedAppInstanceMessageForAdminsSoyTemplateInfo.REQUESTER_NAME, requesterName,
              StoppedAppInstanceMessageForAdminsSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale)
          ))));
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
      notificationPrototype.getAction_uri().set(ULocale.ROOT, urls.myApps().map(URI::toString).orElse(null));
    }

    appAdminHelper.getAdmins(instance)
        .filter(Predicate.isEqual(requesterId).negate())
        .forEach(adminId -> {
          try {
            Notification notification = new Notification(notificationPrototype);
            notification.setUser_id(adminId);
            notificationRepository.createNotification(notification);
          } catch (Exception e) {
            // Don't fail if we can't notify
            logger.error("Error notifying app admin {} after the user {} has stopped the instance {}", adminId, requesterId,
                instance.getName().get(ULocale.ROOT), e);
          }
        });
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

      notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_MESSAGE_FOR_REQUESTER, locale, SanitizedContent.ContentKind.TEXT,
          ImmutableMap.of(
              StoppedAppInstanceMessageForRequesterSoyTemplateInfo.APP_INSTANCE_NAME, instance.getName().get(locale)
          ))));
      if (urls.myApps().isPresent()) {
        notification.getAction_label().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
            ChangedAppInstanceStatusSoyInfo.STOPPED_APP_INSTANCE_ACTION, locale, SanitizedContent.ContentKind.TEXT)));
      }
    }
    if (urls.myApps().isPresent()) {
      notification.getAction_uri().set(ULocale.ROOT, urls.myApps().map(URI::toString).orElse(null));
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

    @Nullable long[] ifMatch();

    AppInstance.InstantiationStatus newStatus();

    boolean notifyAdmins();
  }

  @Value.Immutable
  public static interface Response {
    AppInstance appInstance();

    ResponseStatus responseStatus();
  }

  @Value.Immutable
  public static interface ProviderRequest {
    @JsonProperty String instance_id();

    @JsonProperty AppInstance.InstantiationStatus status();
  }

  public static enum ResponseStatus {
    NOTHING_TO_MODIFY,
    SUCCESS,
    VERSION_CONFLICT,
    NOT_FOUND
  }
}
