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

import oasis.model.InvalidVersionException;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.services.etag.EtagService;
import oasis.web.ResponseFactory;

@Path("/d/dataprovider")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/dataprovider", description = "Application data providers directory API")
public class DataProviderDirectoryEndpoint {

  @Inject
  ApplicationRepository applications;

  @Inject
  EtagService etagService;

  @GET
  @Path("/{dataProviderId}")
  @ApiOperation(value = "Retrieve a data provider",
      notes = "Returns a data provider",
      response = DataProvider.class)
  @ApiResponses({@ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
      message = "The requested data provider does not exist, or no data provider id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested data provider")})
  public Response getDataProvider(@PathParam("dataProviderId") String dataProviderId) {
    DataProvider dataProvider = applications.getDataProvider(dataProviderId);
    if (dataProvider == null) {
      return ResponseFactory.notFound("The requested data provider does not exist");
    }

    return Response.ok()
        .entity(dataProvider)
        .tag(etagService.getEtag(dataProvider))
        .build();
  }

  @PUT
  @Path("/{dataProviderId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates or updates a data provider",
      response = DataProvider.class)
  @ApiResponses({@ApiResponse(code = ResponseFactory.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested data provider does not exist, or no data provider id has been sent"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access the requested data provider"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response putDataProvider(
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("dataProviderId") String dataProviderId,
      @ApiParam DataProvider dataProvider) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    DataProvider updatedDataProvider;
    try {
      updatedDataProvider = applications.updateDataProvider(dataProviderId, dataProvider, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (updatedDataProvider == null) {
      return ResponseFactory.notFound("The requested data provider does not exist");
    }

    URI uri = UriBuilder.fromResource(DataProviderDirectoryEndpoint.class)
        .path(DataProviderDirectoryEndpoint.class, "getDataProvider")
        .build(dataProviderId);
    return Response.created(uri)
        .tag(etagService.getEtag(updatedDataProvider))
        .contentLocation(uri)
        .entity(updatedDataProvider)
        .build();
  }

  @DELETE
  @Path("/{dataProviderId}")
  @ApiOperation(value = "Deletes data provider")
  @ApiResponses({@ApiResponse(code = ResponseFactory.SC_PRECONDITION_REQUIRED,
      message = "The If-Match header is mandatory"),
      @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
          message = "The requested data provider does not exist"),
      @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
          message = "The current user cannot access or delete the requested data provider"),
      @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
          message = "Mismatching etag")})
  public Response deleteDataProvider(
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("dataProviderId") String dataProviderId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    boolean deleted;
    try {
      deleted = applications.deleteDataProvider(dataProviderId, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.notFound("The requested data provider does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }
}
