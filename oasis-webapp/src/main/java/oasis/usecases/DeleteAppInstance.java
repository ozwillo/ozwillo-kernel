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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.services.etag.EtagService;
import oasis.web.webhooks.WebhookSignatureFilter;

@Value.Nested
public class DeleteAppInstance {

  @Inject Provider<Client> clientProvider;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ApplicationRepository applicationRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject CleanupAppInstance cleanupAppInstance;
  @Inject EtagService etagService;

  public Status deleteInstance(Request request, Stats stats) {
    requireNonNull(request);
    requireNonNull(stats);
    if (request.callProvider() || request.checkStatus().isPresent()) {
      AppInstance appInstance = appInstanceRepository.getAppInstance(request.instanceId());
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
          .target(appInstance.getDestruction_uri())
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
