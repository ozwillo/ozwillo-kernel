package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthAuthenticationFilter;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/service/{service_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OAuth
@Api(value = "services", description = "Application services")
public class ServiceEndpoint {
  @Inject ServiceRepository serviceRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;

  @PathParam("service_id") String serviceId;

  @GET
  @ApiOperation(
      value = "Retrieves information about a service",
      response = Service.class
  )
  public Response getService() {
    Service service = serviceRepository.getService(serviceId);
    if (service == null) {
      return ResponseFactory.notFound("Service not found");
    }

    if (!service.isVisible()) {
      // only the admins or designed users should be able to see the service if it's "hidden"
      // TODO: check app_users; for now app_users are all members of the organization (and app_admins ⊆ app_users)
      OAuthPrincipal principal = (OAuthPrincipal) securityContext.getUserPrincipal();
      if (principal == null) {
        return OAuthAuthenticationFilter.challengeResponse();
      }
      String userId = principal.getAccessToken().getAccountId();
      OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(userId, service.getProvider_id());
      if (organizationMembership == null) {
        return ResponseFactory.forbidden("Current user is neither an app_admin or app_user for the service");
      }
    }

    // XXX: don't send secrets over the wire
    service.setSubscription_secret(null);

    return Response.ok()
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @PUT
  @ApiOperation(
      value = "Updates the service",
      response = Service.class
  )
  @Authenticated
  public Response update(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
      Service service
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }
    if (service.getId() != null && !service.getId().equals(serviceId)) {
      return ResponseFactory.unprocessableEntity("id doesn't match the URL");
    }
    Service updatedService = serviceRepository.getService(serviceId);
    if (updatedService == null) {
      return ResponseFactory.NOT_FOUND;
    }

    // TODO: support applications bought by individuals
    if (!isAdminOfOrganization(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), updatedService.getProvider_id())) {
      return ResponseFactory.forbidden("Current user is not an admin of the service's providing organization");
    }

    try {
      service = serviceRepository.updateService(service, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (service == null) {
      // deleted in between?
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok()
        .tag(etagService.getEtag(service))
        .entity(service)
        .build();
  }

  @DELETE
  @ApiOperation("Deletes the service")
  @Authenticated
  public Response delete(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // TODO: support applications bought by individuals
    Service serviceToBeDeleted = serviceRepository.getService(serviceId);
    if (serviceToBeDeleted == null) {
      return ResponseFactory.NOT_FOUND;
    }
    if (!isAdminOfOrganization(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), serviceToBeDeleted.getProvider_id())) {
      return ResponseFactory.forbidden("Current user is not an admin of the service's providing organization");
    }

    boolean deleted;
    try {
      deleted = serviceRepository.deleteService(serviceId, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (!deleted) {
      return ResponseFactory.NOT_FOUND;
    }
    return ResponseFactory.NO_CONTENT;
  }

  private boolean isAdminOfOrganization(String userId, String organizationId) {
    OrganizationMembership organizationMembership = organizationMembershipRepository.getOrganizationMembership(userId, organizationId);
    if (organizationMembership == null) {
      return false;
    }
    return organizationMembership.isAdmin();
  }
}
