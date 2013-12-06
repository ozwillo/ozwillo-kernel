package oasis.web.apps;

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

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.ServiceProvider;
import oasis.services.etag.EtagService;

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
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested service provider does not exist")
          .build();
    }
    return Response.ok()
        .entity(serviceProvider)
        .tag(etagService.getEtag(serviceProvider))
        .build();

  }

  @PUT
  @Path("/{serviceProviderId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Updates a service provider")
  @ApiResponses({@ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
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
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    ServiceProvider sp = applications.getServiceProvider(serviceProviderId);
    if (sp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested service provider does not exist")
          .build();
    }

    // FIXME: Check etag validation with etagService
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(sp)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateServiceProvider(serviceProviderId, serviceProvider);
    // FIXME: update should return the modified object
//    EntityTag etag = new EntityTag(Long.toString(serviceProvider.getModified()));
    return Response.noContent()
//        .tag(etag)
        .build();
  }

  @DELETE
  @Path("/{serviceProviderId}")
  @ApiOperation(value = "Deletes service provider")
  @ApiResponses({@ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
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
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    ServiceProvider sp = applications.getServiceProvider(serviceProviderId);
    if (sp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested service provider does not exist")
          .build();
    }

    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(sp)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    // FIXME: check returned value
    applications.deleteServiceProvider(serviceProviderId);
    return Response.noContent()
        .build();
  }
}
