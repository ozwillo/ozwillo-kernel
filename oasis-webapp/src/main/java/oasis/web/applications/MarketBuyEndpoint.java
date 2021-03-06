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
package oasis.web.applications;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.InternalServerErrorException;
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
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import net.ltgt.jaxrs.webhook.client.WebhookSignatureFilter;
import oasis.auth.AuthModule;
import oasis.jongo.OasisIdHelper;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.CatalogEntry;
import oasis.model.authn.AccessToken;
import oasis.model.authn.ClientType;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.authn.CredentialsService;
import oasis.services.authn.PasswordGenerator;
import oasis.urls.BaseUrls;
import oasis.usecases.DeleteAppInstance;
import oasis.usecases.ImmutableDeleteAppInstance;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.authz.TokenEndpoint;
import oasis.web.utils.ResponseFactory;

@Path("/m/instantiate/{application_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
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
  @Inject AuthModule.Settings settings;
  @Inject BaseUrls baseUrls;
  @Inject Clock clock;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("application_id") String applicationId;

  @POST
  @Portal
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

    final AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();

    instance.setApplication_id(application.getId());
    String userId = accessToken.getAccountId();
    instance.setInstantiator_id(userId);
    instance.setStatus(AppInstance.InstantiationStatus.PENDING);
    instance.setPortal_id(accessToken.getServiceProviderId());

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
            .setInstance_registration_uri(uriInfo.getBaseUriBuilder().path(InstanceRegistrationEndpoint.class).build(instance.getId()))
            .setAuthorization_grant(new AuthorizationGrant(createJwtBearer(instance)))
            .setPortal(instance.getPortal_id())));
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
            .checkVersions(null)
            .notifyAdmins(false)
            .build();
        DeleteAppInstance.Status status = deleteAppInstance.deleteInstance(request, new DeleteAppInstance.Stats());
        if (status != DeleteAppInstance.Status.BAD_INSTANCE_STATUS) {
          logger.error("Error calling App Factory for app={} and user={}: uri={}, status={}", applicationId, userId, application.getInstantiation_uri(), response.getStatusInfo());
          return ResponseFactory.build(Response.Status.BAD_GATEWAY, "Application factory failed");
        }
        // instance has been provisioned despite unsuccessful response from the App Factory; fall through.
        logger.info("Error calling App Factory for app={} and user={} but app was provisioned successfully: status={}", applicationId, userId, application.getInstantiation_uri(), response.getStatusInfo());
      }
    } finally {
      response.close();
    }
    // Get the possibly-updated instance
    instance = appInstanceRepository.getAppInstance(instance.getId());
    return Response.ok(instance).build();
  }

  private String createJwtBearer(AppInstance appInstance) {
    String issuer = getIssuer();
    long issuedAt = clock.instant().getEpochSecond();
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(issuer);
    claims.setAudience(UriBuilder.fromUri(issuer).path(TokenEndpoint.class).build().toString());
    claims.setSubject(appInstance.getId());
    claims.setIssuedAt(NumericDate.fromSeconds(issuedAt));
    claims.setExpirationTime(NumericDate.fromSeconds(issuedAt + settings.jwtBearerDuration.getSeconds()));
    claims.setJwtId(OasisIdHelper.generateId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(settings.keyPair.getPrivate());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setPayload(claims.toJson());
    try {
      return jws.getCompactSerialization();
    } catch (JoseException e) {
      logger.error("Error creating jwt-bearer", e);
      // XXX: use InternalServerErrorException as it won't be logged (we already logged above)
      // FIXME: refactor to somehow return a Response from the resource method.
      throw new InternalServerErrorException(e);
    }
  }

  private String getIssuer() {
    if (baseUrls.canonicalBaseUri().isPresent()) {
      return baseUrls.canonicalBaseUri().get().toString();
    }
    return uriInfo.getBaseUri().toString();
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
    @JsonProperty AuthorizationGrant authorization_grant;
    @JsonProperty String portal;

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

    public CreateInstanceRequest setAuthorization_grant(AuthorizationGrant authorization_grant) {
      this.authorization_grant = authorization_grant;
      return this;
    }

    public CreateInstanceRequest setPortal(String portal) {
      this.portal = portal;
      return this;
    }
  }

  public static class User {
    @JsonProperty String id;
    @JsonProperty String name;
    @JsonProperty @Nullable String email_address;

    public User(String id, String name, @Nullable String email_address) {
      this.id = id;
      this.name = name;
      this.email_address = email_address;
    }
  }

  public static class AuthorizationGrant {
    @JsonProperty String grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    @JsonProperty String assertion;
    // TODO: update when we'll have finer-grain scopes for the DataCore.
    @JsonProperty String scope = "datacore";

    public AuthorizationGrant(String assertion) {
      this.assertion = assertion;
    }
  }
}
