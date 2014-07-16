package oasis.web.applications;

import java.net.URI;
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

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Application;
import oasis.model.authn.ClientType;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.applications.AppInstanceService;
import oasis.services.applications.ApplicationService;
import oasis.services.authn.CredentialsService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.providers.JacksonJsonProvider;
import oasis.web.utils.ResponseFactory;
import oasis.web.webhooks.WebhookSignatureFilter;

@Path("/m/instantiate/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class MarketBuyEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(MarketBuyEndpoint.class);

  @Inject ApplicationService applicationService;
  @Inject DirectoryRepository directoryRepository;
  @Inject AppInstanceService appInstanceService;
  @Inject CredentialsService credentialsService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("application_id") String applicationId;

  @POST
  public Response instantiate(AppInstance instance) {
    Application application = applicationService.getApplication(applicationId);
    if (application == null) {
      return ResponseFactory.notFound("Application doesn't exist");
    }
    if (!Strings.isNullOrEmpty(instance.getProvider_id())) {
      Organization organization = directoryRepository.getOrganization(instance.getProvider_id());
      if (organization == null) {
        return ResponseFactory.unprocessableEntity("Organization doesn't exist");
      }
    }

    instance.setApplication_id(application.getId());
    String userId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    instance.setInstantiator_id(userId);
    instance.setStatus(AppInstance.InstantiationStatus.PENDING);

    instance = appInstanceService.createAppInstance(instance);
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
            .setInstance_registration_uri(uriInfo.getBaseUriBuilder().path(InstanceRegistrationEndpoint.class).build(instance.getId()))));
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
      appInstanceService.deletePendingInstance(instance.getId());
      return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
    }
    // Get the possibly-updated instance
    instance = appInstanceService.getAppInstance(instance.getId());
    return Response.ok(instance).build();
  }

  public static class CreateInstanceRequest {
    @JsonProperty
    String instance_id;
    @JsonProperty String client_id;
    @JsonProperty String client_secret;
    @JsonProperty
    URI instance_registration_uri;

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

    public CreateInstanceRequest setInstance_registration_uri(URI instance_registration_uri) {
      this.instance_registration_uri = instance_registration_uri;
      return this;
    }
  }
}
