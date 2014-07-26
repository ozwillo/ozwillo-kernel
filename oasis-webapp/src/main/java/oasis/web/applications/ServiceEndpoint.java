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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/apps/service/{service_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "services", description = "Application services")
public class ServiceEndpoint {
  @Inject ServiceRepository serviceRepository;
  @Inject EtagService etagService;

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
  @Authenticated @OAuth
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
    // TODO: check the user is an admin of the service's providing organization (TODO: support services bought by individuals)
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
  public Response delete(
      @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch
  ) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }
    // TODO: check the user is an admin of the service's providing organization (TODO: support services bought by individuals)
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
}
