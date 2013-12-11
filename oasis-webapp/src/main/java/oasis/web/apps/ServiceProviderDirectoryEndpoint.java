package oasis.web.apps;

import java.net.URI;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.ServiceProvider;
import oasis.services.etag.EtagService;
import oasis.web.ResponseFactory;

@Path("/d/serviceprovider")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/serviceprovider", description = "Application service providers directory API")
public class ServiceProviderDirectoryEndpoint {

  @Inject
  ApplicationRepository applications;

  @Inject
  EtagService etagService;

  @GET
  @Path("/{serviceProviderId}")
  @ApiOperation(value = "Retrieve a service provider",
      notes = "Returns a service provider",
      response = ServiceProvider.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested service provider does not exist, or no service provider id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested service provider")})
  public Response getServiceProvider(
      @PathParam("serviceProviderId") String serviceProviderId) {
    ServiceProvider serviceProvider = applications.getServiceProvider(serviceProviderId);
    if (serviceProvider == null) {
      return ResponseFactory.notFound("The requested service provider does not exist");
    }
    return Response.ok()
        .entity(serviceProvider)
        .tag(etagService.getEtag(serviceProvider))
        .build();

  }

  @PUT
  @Path("/{serviceProviderId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Updates a service provider",
      response = ServiceProvider.class)
  @ApiResponses({@ApiResponse(code = ResponseFactory.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested service provider does not exist, or no service provider id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested service provider"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response putServiceProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("serviceProviderId") String serviceProviderId,
      @ApiParam ServiceProvider serviceProvider) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // TODO: remove this check, validate etag in the update operation
    ServiceProvider sp = applications.getServiceProvider(serviceProviderId);
    if (sp == null) {
      return ResponseFactory.notFound("The requested service provider does not exist");
    }

    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(sp)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    ServiceProvider updatedServiceProvider = applications.updateServiceProvider(serviceProviderId, serviceProvider);
    if (updatedServiceProvider == null) {
      return ResponseFactory.notFound("The requested service provider does not exist");
    }

    URI uri = UriBuilder.fromResource(ServiceProviderDirectoryEndpoint.class)
        .path(ServiceProviderDirectoryEndpoint.class, "getServiceProvider")
        .build(serviceProviderId);
    return Response.created(uri)
        .tag(etagService.getEtag(updatedServiceProvider))
        .contentLocation(uri)
        .entity(updatedServiceProvider)
        .build();
  }

  @DELETE
  @Path("/{serviceProviderId}")
  @ApiOperation(value = "Deletes service provider")
  @ApiResponses({@ApiResponse(code = ResponseFactory.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested service provider does not exist"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access or delete the requested service provider"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response deleteServiceProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("serviceProviderId") String serviceProviderId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // TODO: remove this check, validate etag in the delete operation
    ServiceProvider sp = applications.getServiceProvider(serviceProviderId);
    if (sp == null) {
      return ResponseFactory.notFound("The requested service provider does not exist");
    }

    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(sp)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    if (!applications.deleteServiceProvider(serviceProviderId)) {
      return ResponseFactory.notFound("The requested service provider does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }
}
