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
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.authn.ClientType;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.authn.CredentialsService;
import oasis.services.authn.PasswordGenerator;
import oasis.usecases.DeleteAppInstance;
import oasis.usecases.ImmutableDeleteAppInstance;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
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
  @Inject AccountRepository accountRepository;
  @Inject PasswordGenerator passwordGenerator;
  @Inject CredentialsService credentialsService;
  @Inject DeleteAppInstance deleteAppInstance;
  @Inject Client client;

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
    String pwd = passwordGenerator.generate();
    credentialsService.setPassword(ClientType.PROVIDER, instance.getId(), pwd);

    Future<Response> future = client
        .target(application.getInstantiation_uri())
        .register(new WebhookSignatureFilter(application.getInstantiation_secret()))
        .request()
        .async()
        .post(Entity.json(new CreateInstanceRequest()
            .setInstance_id(instance.getId())
            .setClient_id(instance.getId())
            .setClient_secret(pwd)
            .setUser(accountRepository.getUserAccountById(userId))
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
    try {
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        ImmutableDeleteAppInstance.Request request = ImmutableDeleteAppInstance.Request.builder()
            .instanceId(instance.getId())
            .callProvider(false)
            .checkStatus(AppInstance.InstantiationStatus.PENDING)
            .checkVersions(Optional.<long[]>absent())
            .build();
        DeleteAppInstance.Status status = deleteAppInstance.deleteInstance(request, new DeleteAppInstance.Stats());
        if (status != DeleteAppInstance.Status.BAD_INSTANCE_STATUS) {
          return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
        }
        // instance has been provisioned despite unsuccessful response from the App Factory; fall through.
      }
    } finally {
      response.close();
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
    @JsonProperty User user;
    @JsonProperty String organization_id;
    @JsonProperty String organization_name;
    @JsonProperty Organization organization;
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

    public CreateInstanceRequest setUser(UserAccount user) {
      this.user = new User(user.getId(), user.getDisplayName(), user.getEmail_address());
      this.user_id = user.getId();
      return this;
    }

    public CreateInstanceRequest setOrganization(Organization organization) {
      if (organization != null) {
        // Copy to only include Organization fields, and restore the ID (not copied around)
        this.organization = new Organization(organization);
        this.organization.setId(organization.getId());

        organization_id = organization.getId();
        organization_name = organization.getName();
      } else {
        this.organization = null;
        organization_id = organization_name = null;
      }
      return this;
    }

    public CreateInstanceRequest setInstance_registration_uri(URI instance_registration_uri) {
      this.instance_registration_uri = instance_registration_uri;
      return this;
    }
  }

  public static class User {
    @JsonProperty String id;
    @JsonProperty String name;
    @JsonProperty String email_address;

    public User(String id, String name, String email_address) {
      this.id = id;
      this.name = name;
      this.email_address = email_address;
    }
  }
}
