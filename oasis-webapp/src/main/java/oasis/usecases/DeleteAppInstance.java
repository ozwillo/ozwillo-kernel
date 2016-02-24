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
package oasis.usecases;

import static java.util.Objects.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import org.immutables.value.Value;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import net.ltgt.jaxrs.webhook.client.WebhookSignatureFilter;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.DeletedAppInstanceSoyInfo;
import oasis.soy.templates.DeletedAppInstanceSoyInfo.DeletedAppInstanceMessageSoyTemplateInfo;
import oasis.web.i18n.LocaleHelper;

@Value.Enclosing
public class DeleteAppInstance {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAppInstance.class);

  @Inject Provider<Client> clientProvider;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ApplicationRepository applicationRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject CleanupAppInstance cleanupAppInstance;
  @Inject EtagService etagService;
  @Inject SoyTemplateRenderer templateRenderer;

  public Status deleteInstance(Request request, Stats stats) {
    requireNonNull(request);
    requireNonNull(stats);

    AppInstance appInstance = null;
    Iterable<String> adminIds = null;
    if (request.callProvider() || request.checkStatus().isPresent() || request.notifyAdmins()) {
      appInstance = appInstanceRepository.getAppInstance(request.instanceId());
      if (appInstance != null) {
        if (request.checkVersions().isPresent() && !etagService.hasEtag(appInstance, request.checkVersions().get())) {
          return Status.BAD_INSTANCE_VERSION;
        }
        if (request.checkStatus().isPresent()) {
          @Nullable Status status = checkStatus(appInstance, request.checkStatus().get());
          if (status != null) {
            return status;
          }
        }
        if (request.callProvider()) {
          @Nullable Status status = callProvider(appInstance);
          if (status != null) {
            return status;
          }
        }
        if (request.notifyAdmins()) {
          adminIds = appAdminHelper.getAdmins(appInstance);
        }
      }
    }

    // XXX: we first delete the instance, then all the orphan data: ACL, services, scopes, etc.
    // Only use checkVersions if we haven't issued a request to the provider yet!
    if (request.checkVersions().isPresent() && !request.callProvider() && !request.checkStatus().isPresent()) {
      try {
        stats.appInstanceDeleted = appInstanceRepository.deleteInstance(request.instanceId(), request.checkVersions().get());
      } catch (InvalidVersionException e) {
        stats.appInstanceDeleted = false;
        return Status.BAD_INSTANCE_VERSION;
      }
    } else {
      stats.appInstanceDeleted = appInstanceRepository.deleteInstance(request.instanceId());
    }
    stats.credentialsDeleted = credentialsRepository.deleteCredentials(ClientType.PROVIDER, request.instanceId());

    cleanupAppInstance.cleanupInstance(request.instanceId(), stats);

    if (request.notifyAdmins() && appInstance != null && adminIds != null) {
      notifyAdmins(appInstance, adminIds);
    }

    if (stats.appInstanceDeleted) {
      return Status.DELETED_INSTANCE;
    } else if (stats.isEmpty()) {
      return Status.NOTHING_TO_DELETE;
    }
    return Status.DELETED_LEFTOVERS;
  }

  private @Nullable Status callProvider(AppInstance appInstance) {
    String endpoint, secret;
    switch (appInstance.getStatus()) {
      case RUNNING:
      case STOPPED:
        endpoint = appInstance.getDestruction_uri();
        secret = appInstance.getDestruction_secret();
        break;
      case PENDING:
      default: {
        Application app = applicationRepository.getApplication(appInstance.getApplication_id());
        if (app == null) {
          // XXX: what should we do here? For now fall through and delete the instance, so it will be impossible to provision it.
          return null;
        }
        endpoint = app.getCancellation_uri();
        secret = app.getCancellation_secret();
        break;
      }
    }
    // FIXME: temporarily allow empty destruction_uri
    if (!Strings.isNullOrEmpty(endpoint)) {
      Future<Response> future = clientProvider.get()
          .target(endpoint)
          .register(new WebhookSignatureFilter(secret))
          .request()
          .async()
          .post(Entity.json(new ProviderRequest(appInstance.getId())));
      try {
        // TODO: make timeout configurable
        Response response = future.get(1, TimeUnit.MINUTES);
        try {
          if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return Status.PROVIDER_STATUS_ERROR;
          }
        } finally {
          response.close();
        }
      } catch (InterruptedException | ExecutionException e) {
        // FIXME: check that wrapped exception is not a ResponseProcessingException
        return Status.PROVIDER_CALL_ERROR;
      } catch (TimeoutException e) {
        // Ignore timeouts and fall through (delete the instance)
      }
    }
    return null;
  }

  private void notifyAdmins(AppInstance appInstance, Iterable<String> adminIds) {
    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      SoyMapData data = new SoyMapData();
      data.put(DeletedAppInstanceMessageSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale));

      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          DeletedAppInstanceSoyInfo.DELETED_APP_INSTANCE_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
    }

    for (String adminId : adminIds) {
      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(adminId);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admin {} after deleting the instance {}", adminId, appInstance.getName().get(ULocale.ROOT), e);
      }
    }
  }

  private @Nullable Status checkStatus(AppInstance appInstance, AppInstance.InstantiationStatus checkStatus) {
    if (appInstance.getStatus() != checkStatus) {
      return Status.BAD_INSTANCE_STATUS;
    }
    return null;
  }

  @Value.Immutable
  public static interface Request {
    String instanceId();

    boolean callProvider();

    public boolean notifyAdmins();

    Optional<AppInstance.InstantiationStatus> checkStatus();

    Optional<long[]> checkVersions();
  }

  @NotThreadSafe
  public static class Stats extends CleanupAppInstance.Stats {
    public boolean appInstanceDeleted;
    public boolean credentialsDeleted;

    public boolean isEmpty() {
      return !appInstanceDeleted
          && !credentialsDeleted
          && super.isEmpty();
    }
  }

  public enum Status {
    /** Returned if {@link Request#checkVersions} is present and hte current instance version doesn't match. */
    BAD_INSTANCE_VERSION,
    /** Returned if {@link Request#checkStatus} is present and the current instance status doesn't match. */
    BAD_INSTANCE_STATUS,
    /** Returned if {@link Request#callProvider} is {@code true} and we couldn't call the provider. */
    PROVIDER_CALL_ERROR,
    /** Returned if {@link Request#callProvider} is {@code true} and it responded with an unsuccessful HTTP status code. */
    PROVIDER_STATUS_ERROR,
    /** Returned if the instance has been deleted. */
    DELETED_INSTANCE,
    /** Returned if no instance has been deleted but we deleted any other related data. */
    DELETED_LEFTOVERS,
    /** Returned if we deleted absolutely nothing. */
    NOTHING_TO_DELETE,
  }

  private static class ProviderRequest {
    @JsonProperty String instance_id;

    public ProviderRequest(String instance_id) {
      this.instance_id = instance_id;
    }
  }
}
