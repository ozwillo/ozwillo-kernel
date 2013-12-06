package oasis.web.apps;

import java.net.URI;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.ServiceProvider;
import oasis.services.etag.EtagService;

@Path("/d/app")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/app", description = "Application directory API")
public class ApplicationDirectoryEndpoint {

  @Inject
  ApplicationRepository applications;
  @Inject
  EtagService etagService;

  @GET
  @ApiOperation(value = "Retrieve available applications",
      notes = "Returns application array",
      response = Application.class,
      responseContainer = "Array")
  public Response getApplications(
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit) {
    return Response.ok()
        .entity(applications.getApplicationInstances(start, limit).iterator())
        .build();
  }

  @GET
  @Path("/{applicationId}")
  @ApiOperation(value = "Retrieve requested application",
      notes = "Returns application",
      response = Application.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested application does not exist, or no application id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested application")})
  public Response getApplication(@PathParam("applicationId") String applicationId) {
    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist")
          .build();
    }

    return Response.ok()
        .tag(etagService.getEtag(app))
        .entity(app)
        .build();
  }

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates an application",
      response = Application.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
      message = "The current user cannot create applications")})
  public Response postApplication(
      @ApiParam("application") Application application) {

    Application newApp = applications.createApplication(application);

    URI uri = UriBuilder.fromResource(ApplicationDirectoryEndpoint.class)
        .path(ApplicationDirectoryEndpoint.class, "getApplication")
        .build(newApp.getId());
    return Response.created(uri)
        .tag(etagService.getEtag(newApp))
        .contentLocation(uri)
        .entity(newApp)
        .build();
  }

  @PUT
  @Path("/{applicationId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Updates an application")
  @ApiResponses({@ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot create applications"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response putApplication(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("applicationId") String applicationId,
      @ApiParam("application") Application application) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }
    Application app = applications.getApplication(applicationId);
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(app)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateApplication(applicationId, application);
    // FIXME: update should return the updated object
//    etag = new EntityTag(Long.toString(application.getModified()));
    return Response.noContent()
//        .tag(etag)
        .build();
  }

  @DELETE
  @Path("/{applicationId}")
  @ApiOperation(value = "Deletes an application")
  @ApiResponses({@ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested application does not exist, or no application id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access or delete the requested application"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response deleteApplication(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("applicationId") String applicationId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist")
          .build();
    }

    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(new EntityTag(etagService.getEtag(app)));
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.deleteApplication(applicationId);
    // FIXME: check returned value
    return Response.noContent()
        .build();
  }

  @GET
  @Path("/{applicationId}/dataproviders")
  @ApiOperation(value = "Retrieve data providers of an application",
      notes = "Returns data providers array",
      response = DataProvider.class,
      responseContainer = "Array")
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested application does not exist, or no application id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested application")})
  public Response getDataProviders(@PathParam("applicationId") String applicationId) {
    Iterable<DataProvider> dataProviders = applications.getDataProviders(applicationId);
    if (dataProviders == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist")
          .build();
    }

    return Response.ok()
        .entity(dataProviders)
        .build();
  }

  @POST
  @Path("/{applicationId}/dataproviders")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates a data provider",
      response = DataProvider.class)
  @ApiResponses({@ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested application does not exist, or no application id has been sent"),
      @ApiResponse(code = oasis.web.Application.SC_UNPROCESSABLE_ENTITY,
          message = "The requested application instance cannot be updated"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested application")})
  public Response postDataProvider(
      @PathParam("applicationId") String applicationId,
      @ApiParam DataProvider dataProvider) {
    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist")
          .build();
    }

    if (app.isTenant()) {
      return Response.status(oasis.web.Application.SC_UNPROCESSABLE_ENTITY)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application instance cannot be updated")
          .build();
    }

    DataProvider newDataProvider = applications.createDataProvider(applicationId, dataProvider);
    URI uri = UriBuilder.fromResource(DataProviderDirectoryEndpoint.class)
        .path(DataProviderDirectoryEndpoint.class, "getDataProvider")
        .build(newDataProvider.getId());

    return Response.created(uri)
        .tag(etagService.getEtag(newDataProvider))
        .contentLocation(uri)
        .entity(newDataProvider)
        .build();
  }

  @GET
  @Path("/{applicationId}/serviceprovider")
  @ApiOperation(value = "Retrieve the service provider of an application",
      notes = "Returns a service provider",
      response = ServiceProvider.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested application does not exist, no application id has been sent or the application does not contain a service provider"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested application")})
  public Response getServiceProvider(@PathParam("applicationId") String applicationId) {
    ServiceProvider serviceProvider = applications.getServiceProviderFromApplication(applicationId);
    if (serviceProvider == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist or does not contain a service provider")
          .build();
    }

    return Response.ok()
        .tag(etagService.getEtag(serviceProvider))
        .entity(serviceProvider)
        .build();
  }

  @POST
  @Path("/{applicationId}/serviceprovider")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates a service provider",
      response = ServiceProvider.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested application does not exist, no application id has been sent or no service provider can be found for this application"),
      @ApiResponse(code = oasis.web.Application.SC_UNPROCESSABLE_ENTITY,
          message = "The requested application instance cannot be updated"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested application")})
  public Response postServiceProvider(
      @PathParam("applicationId") String applicationId,
      @ApiParam ServiceProvider serviceProvider) {
    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application does not exist")
          .build();
    }

    if (app.isTenant()) {
      return Response.status(oasis.web.Application.SC_UNPROCESSABLE_ENTITY)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application instance cannot be updated")
          .build();
    }

    ServiceProvider newServiceProvider = applications.createServiceProvider(applicationId, serviceProvider);
    URI uri = UriBuilder.fromResource(ServiceProviderDirectoryEndpoint.class)
        .path(ServiceProviderDirectoryEndpoint.class, "getServiceProvider")
        .build(newServiceProvider.getId());

    return Response.created(uri)
        .tag(etagService.getEtag(newServiceProvider))
        .contentLocation(uri)
        .entity(newServiceProvider)
        .build();
  }
}
