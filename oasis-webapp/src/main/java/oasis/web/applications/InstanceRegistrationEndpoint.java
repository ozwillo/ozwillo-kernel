package oasis.web.applications;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent;
import com.ibm.icu.util.ULocale;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstance.NeededScope;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.AppProvisioningSoyInfo;
import oasis.urls.Urls;
import oasis.usecases.CleanupAppInstance;
import oasis.usecases.DeleteAppInstance;
import oasis.usecases.ServiceValidator;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;

@Path("/apps/pending-instance/{instance_id}")
@Authenticated @Client
@Api(value = "instance-registration", description = "Application Factories' callback")
public class InstanceRegistrationEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(InstanceRegistrationEndpoint.class);

  @Inject ServiceValidator serviceValidator;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject ScopeRepository scopeRepository;
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject DeleteAppInstance deleteAppInstance;
  @Inject CleanupAppInstance cleanupAppInstance;
  @Inject Urls urls;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject NotificationRepository notificationRepository;

  @PathParam("instance_id") String instanceId;

  @Context SecurityContext securityContext;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
      value = "Acknowledges the provisioning of an instance",
      notes = "See the <a href='https://docs.google.com/document/d/1V0lmEPTVl_UH7Dl-6AsiedALviJvjHW7RGw5jYg0Ah8/edit?usp=sharing'>Application Provisioning Protocol</a>"
  )
  public Response instantiated(
      @Context UriInfo uriInfo,
      AcknowledgementRequest acknowledgementRequest) {
    if (!((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(instanceId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (!instanceId.equals(acknowledgementRequest.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("instance_id doesn't match URL");
    }

    // FIXME: don't mandate a destruction_uri yet, but error out if provided without a destruction_secret.
    if (acknowledgementRequest.destruction_uri != null && Strings.isNullOrEmpty(acknowledgementRequest.destruction_secret)) {
      return ResponseFactory.unprocessableEntity("Missing destruction_secret");
    }
    @Nullable Response error = acknowledgementRequest.checkScopes(instanceId);
    if (error != null) {
      return error;
    }
    error = acknowledgementRequest.checkServices(serviceValidator, instanceId);
    if (error != null) {
      return error;
    }
    error = acknowledgementRequest.checkNeededScopes(scopeRepository);
    if (error != null) {
      return error;
    }

    AppInstance instance = appInstanceRepository.instantiated(instanceId, acknowledgementRequest.getNeeded_scopes(),
        acknowledgementRequest.destruction_uri, acknowledgementRequest.destruction_secret);
    if (instance == null) {
      return ResponseFactory.notFound("Pending instance not found");
    }

    Map<String, String> acknowledgementResponse = new LinkedHashMap<>(
        acknowledgementRequest.getServices().size());
    try {
      for (Scope scope : acknowledgementRequest.getScopes()) {
        scope.setInstance_id(instanceId);
        scope.computeId();
        scopeRepository.createOrUpdateScope(scope);
      }
      for (Service service : acknowledgementRequest.getServices()) {
        service.setInstance_id(instanceId);
        service.setProvider_id(instance.getProvider_id());
        service = serviceRepository.createService(service);
        acknowledgementResponse.put(service.getLocal_id(), service.getId());

        // Automatically subscribe the "instantiator" to all services
        UserSubscription subscription = new UserSubscription();
        subscription.setUser_id(instance.getInstantiator_id());
        subscription.setService_id(service.getId());
        subscription.setCreator_id(instance.getInstantiator_id());
        subscription.setSubscription_type(
            instance.getProvider_id() == null
                ? UserSubscription.SubscriptionType.PERSONAL
                : UserSubscription.SubscriptionType.ORGANIZATION);
        userSubscriptionRepository.createUserSubscription(subscription);
      }
    } catch (Throwable t) {
      appInstanceRepository.backToPending(instanceId);
      cleanupAppInstance.cleanupInstance(instanceId, new DeleteAppInstance.Stats());
    }

    // If everything's OK, notify the instantiator
    if (urls.myApps().isPresent()) {
      try {
        Notification notification = new Notification();
        notification.setInstance_id(instance.getId());
        notification.setUser_id(instance.getInstantiator_id());
        for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
          ULocale messageLocale = locale;
          if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
            messageLocale = ULocale.ROOT;
          }
          notification.getMessage().set(messageLocale, templateRenderer.renderAsString(
              new SoyTemplate(AppProvisioningSoyInfo.MESSAGE, locale, SanitizedContent.ContentKind.TEXT)));
          notification.getAction_label().set(messageLocale, templateRenderer.renderAsString(
              new SoyTemplate(AppProvisioningSoyInfo.ACTION_LABEL, locale, SanitizedContent.ContentKind.TEXT)));
        }
        notification.getAction_uri().set(ULocale.ROOT, urls.myApps().get().toString());
        notification.setTime(Instant.now());
        notification.setStatus(Notification.Status.UNREAD);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying instantiator after successful provisioning of {}", instanceId, e);
      }
    }

    return Response.created(Resteasy1099.getBaseUriBuilder(uriInfo).path(AppInstanceEndpoint.class).build(instanceId))
        .entity(new GenericEntity<Map<String, String>>(acknowledgementResponse) {})
        .build();
  }

  @DELETE
  @ApiOperation(
      value = "Notifies an error while provisioning the instance",
      notes = "See the <a href='https://docs.google.com/document/d/1V0lmEPTVl_UH7Dl-6AsiedALviJvjHW7RGw5jYg0Ah8/edit?usp=sharing'>Application Provisioning Protocol</a>"
  )
  public Response errorInstantiating() {
    if (!((ClientPrincipal) securityContext.getUserPrincipal()).getClientId().equals(instanceId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    DeleteAppInstance.Request request = new DeleteAppInstance.Request(instanceId);
    request.callProvider = false;
    request.checkStatus = Optional.of(AppInstance.InstantiationStatus.PENDING);
    request.checkVersions = Optional.absent();
    DeleteAppInstance.Status status = deleteAppInstance.deleteInstance(request, new DeleteAppInstance.Stats());

    switch (status) {
      case BAD_INSTANCE_STATUS:
        // Instance has already been provisioned
        return ResponseFactory.NOT_FOUND;
      case DELETED_INSTANCE:
      // below are race-condition: we couldn't have authenticated the client otherwise
      case DELETED_LEFTOVERS:
      case NOTHING_TO_DELETE:
        return Response.ok().build();
      default:
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  public static class AcknowledgementRequest {
    @JsonProperty String instance_id;
    @JsonProperty List<Service> services;
    @JsonProperty List<Scope> scopes;
    @JsonProperty List<NeededScope> needed_scopes;
    @JsonProperty String destruction_uri;
    @JsonProperty String destruction_secret;

    public String getInstance_id() {
      return instance_id;
    }

    public List<Service> getServices() {
      if (services == null) {
        services = new ArrayList<>();
      }
      return services;
    }

    public List<Scope> getScopes() {
      if (scopes == null) {
        scopes = new ArrayList<>();
      }
      return scopes;
    }

    public List<NeededScope> getNeeded_scopes() {
      if (needed_scopes == null) {
        needed_scopes = new ArrayList<>();
      }
      return needed_scopes;
    }

    @Nullable Response checkServices(ServiceValidator serviceValidator, String instance_id) {
      if (services == null) {
        return null;
      }
      Set<String> serviceIds = Sets.newHashSetWithExpectedSize(services.size());
      for (Service service : services) {
        @Nullable String error = serviceValidator.validateService(service, instance_id);
        if (error != null) {
          return ResponseFactory.unprocessableEntity(error);
        }
        if (!serviceIds.add(service.getLocal_id())) {
          return ResponseFactory.unprocessableEntity("Duplicate services: " + service.getLocal_id());
        }
      }
      return null;
    }

    @Nullable Response checkScopes(String instance_id) {
      if (scopes == null) {
        return null;
      }
      for (Scope scope : scopes) {
        if (Strings.isNullOrEmpty(scope.getLocal_id())) {
          return ResponseFactory.unprocessableEntity("Scope missing local_id");
        }
        // TODO: validate all provided names (here, we enforce presence of a ROOT value)
        if (Strings.isNullOrEmpty(scope.getName().get(ULocale.ROOT))) {
          return ResponseFactory.unprocessableEntity("Scope missing name: " + scope.getLocal_id());
        }
        // XXX: description?
        if (scope.getInstance_id() != null && !scope.getInstance_id().equals(instance_id)) {
          return ResponseFactory.unprocessableEntity("Bad scope instance_id");
        }
      }
      return null;
    }

    @Nullable Response checkNeededScopes(ScopeRepository scopeRepository) {
      if (needed_scopes == null || needed_scopes.isEmpty()) {
        return null;
      }

      Set<String> neededScopeIds = Sets.newHashSetWithExpectedSize(needed_scopes.size());
      for (NeededScope neededScope : needed_scopes) {
        if (Strings.isNullOrEmpty(neededScope.getScope_id())) {
          return ResponseFactory.unprocessableEntity("needed_scope missing scope_id");
        }
        if (!neededScopeIds.add(neededScope.getScope_id())) {
          return ResponseFactory.unprocessableEntity("Duplicate needed_scopes: " + neededScope.getScope_id());
        }
        // XXX: motivation?
      }
      try {
        scopeRepository.getScopes(neededScopeIds);
      } catch (IllegalArgumentException iae) {
        return ResponseFactory.unprocessableEntity("needed_scopes references nonexistent scope_id");
      }
      return null;
    }
  }
}
