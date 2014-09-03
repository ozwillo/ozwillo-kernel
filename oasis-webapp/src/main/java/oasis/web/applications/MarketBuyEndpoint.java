package oasis.web.applications;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.authn.CredentialsService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.providers.JacksonJsonProvider;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.utils.ResponseFactory;
import oasis.web.webhooks.WebhookSignatureFilter;

@Path("/m/instantiate/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "buy", description = "Buying/picking an application")
public class MarketBuyEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(MarketBuyEndpoint.class);

  @Inject ApplicationRepository applicationRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject CredentialsService credentialsService;
  @Inject CredentialsRepository credentialsRepository;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("application_id") String applicationId;

  @POST
  @ApiOperation(
      value = "Instantiates an application",
      response = AppInstance.class
  )
  public Response instantiate(AppInstance instance) {
    Application application = applicationRepository.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application doesn't exist");
    }
    // XXX: some legacy applications don't have a target audience
    List<CatalogEntry.TargetAudience> targetAudiences = application.getTarget_audience();
    if (targetAudiences == null || targetAudiences.isEmpty()) {
      targetAudiences = ImmutableList.copyOf(CatalogEntry.TargetAudience.values());
    }

    Organization organization;
    if (!Strings.isNullOrEmpty(instance.getProvider_id())) {
      organization = directoryRepository.getOrganization(instance.getProvider_id());
      if (organization == null) {
        return ResponseFactory.unprocessableEntity("Organization doesn't exist");
      }
      // TODO: refactor application target_audience check
      // XXX: some legacy organizations don't have a type
      if (organization.getType() != null) {
        switch (organization.getType()) {
          case PUBLIC_BODY:
            if (!targetAudiences.contains(CatalogEntry.TargetAudience.PUBLIC_BODIES)) {
              return ResponseFactory.conflict("Application is not targeted at public bodies");
            }
            break;
          case COMPANY:
            if (!targetAudiences.contains(CatalogEntry.TargetAudience.COMPANIES)) {
              return ResponseFactory.conflict("Application is not targeted at companies");
            }
            break;
          default:
            // That shouldn't happen, but let's handle the degenerate case
            if (targetAudiences.equals(ImmutableList.of(CatalogEntry.TargetAudience.CITIZENS))) {
              return ResponseFactory.conflict("Application is not targeted at organizations");
            }
            break;
        }
      } else {
        if (targetAudiences.equals(ImmutableList.of(CatalogEntry.TargetAudience.CITIZENS))) {
          return ResponseFactory.conflict("Application is not targeted at organizations");
        }
      }
    } else {
      organization = null;
      if (!targetAudiences.contains(CatalogEntry.TargetAudience.CITIZENS)) {
        return ResponseFactory.conflict("Application is not targeted at citizens");
      }
    }

    instance.setApplication_id(application.getId());
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    instance.setInstantiator_id(userId);
    instance.setStatus(AppInstance.InstantiationStatus.PENDING);

    instance = appInstanceRepository.createAppInstance(instance);
    // FIXME: temporarily set the password to the instance id
    credentialsService.setPassword(ClientType.PROVIDER, instance.getId(), instance.getId());

    Future<Response> future = ClientBuilder.newClient()
        .target(application.getInstantiation_uri())
        .register(new WebhookSignatureFilter(application.getInstantiation_secret()))
        .register(JacksonJsonProvider.class)
        .request()
        .async()
        .post(Entity.json(new CreateInstanceRequest()
            .setInstance_id(instance.getId())
            .setClient_id(instance.getId())
            .setClient_secret(instance.getId())
            .setUser_id(userId)
            .setOrganization(organization)
            .setInstance_registration_uri(Resteasy1099.getBaseUriBuilder(uriInfo).path(InstanceRegistrationEndpoint.class).build(instance.getId()))));
    Response response;
    try {
      response = future.get(1, TimeUnit.MINUTES);
    } catch (InterruptedException | ExecutionException e) {
      logger.error("Error calling App Factory for app={} and user={}", applicationId, userId, e);
      return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
    } catch (TimeoutException e) {
      logger.error("Timeout calling App Factory for app={} and user={}", applicationId, userId, e);
      return ResponseFactory.build(Response.Status.GATEWAY_TIMEOUT, "Application factory timed-out");
    }
    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
      appInstanceRepository.deletePendingInstance(instance.getId());
      credentialsRepository.deleteCredentials(ClientType.PROVIDER, instance.getId());
      return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
    }
    // Get the possibly-updated instance
    instance = appInstanceRepository.getAppInstance(instance.getId());
    return Response.ok(instance).build();
  }

  public static class CreateInstanceRequest {
    @JsonProperty String instance_id;
    @JsonProperty String client_id;
    @JsonProperty String client_secret;
    @JsonProperty String user_id;
    @JsonProperty String organization_id;
    @JsonProperty String organization_name;
    @JsonProperty URI instance_registration_uri;

    public CreateInstanceRequest setInstance_id(String instance_id) {
      this.instance_id = instance_id;
      return this;
    }

    public CreateInstanceRequest setClient_id(String client_id) {
      this.client_id = client_id;
      return this;
    }

    public CreateInstanceRequest setClient_secret(String client_secret) {
      this.client_secret = client_secret;
      return this;
    }

    public CreateInstanceRequest setUser_id(String user_id) {
      this.user_id = user_id;
      return this;
    }

    public CreateInstanceRequest setOrganization(Organization organization) {
      if (organization != null) {
        organization_id = organization.getId();
        organization_name = organization.getName();
      } else {
        organization_id = organization_name = null;
      }
      return this;
    }

    public CreateInstanceRequest setInstance_registration_uri(URI instance_registration_uri) {
      this.instance_registration_uri = instance_registration_uri;
      return this;
    }
  }
}
