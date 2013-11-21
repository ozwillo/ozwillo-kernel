package oasis.web.apps;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.applications.Scopes;

@Path("/d/dataprovider")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/dataprovider", description = "Application data providers directory API")
public class DataProviderDirectoryResource {

  @Inject
  private ApplicationRepository applications;

  @GET
  @Path("/{dataProviderId}")
  @ApiOperation(value = "Retrieve a data provider",
                notes = "Returns a data provider",
                response = DataProvider.class)
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested data provider does not exist, or no data provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested data provider") })
  public Response getDataProvider(@PathParam("dataProviderId") String dataProviderId) {
    DataProvider dataProvider = applications.getDataProvider(dataProviderId);
    if (dataProvider != null) {
      EntityTag etag = new EntityTag(Long.toString(dataProvider.getModified()));
      return Response.ok()
          .entity(dataProvider)
          .tag(etag)
          .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested data provider does not exist")
          .build();
    }
  }

  @GET
  @Path("/{dataProviderId}/scopes")
  @ApiOperation(value = "Retrieve scopes provided by a data provider",
                notes = "Returns a Scope array",
                response = Scopes.class)
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested data provider does not exist, or no data provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested data provider") })
  public Response getDataProviderScopes(@PathParam("dataProviderId") String dataProviderId) {
    Scopes scopes = applications.getProvidedScopes(dataProviderId);
    if (scopes != null) {
      EntityTag etag = new EntityTag(Long.toString(scopes.getModified()));
      return Response.ok()
          .entity(scopes)
          .tag(etag)
          .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested data provider does not exist")
          .build();
    }
  }

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates or updates a data provider")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested data provider") })
  public Response postDataProvider(
      @Context UriInfo uriInfo,
      @ApiParam DataProvider dataProvider) throws URISyntaxException {
    String dataProviderId = applications.createDataProvider(dataProvider);
    EntityTag etag = new EntityTag(Long.toString(dataProvider.getModified()));
    URI res = new URI(uriInfo.getRequestUri().toString() + dataProviderId);
    return Response.created(res)
        .tag(etag)
        .build();
  }

  @PUT
  @Path("/{dataProviderId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates or updates a data provider")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested data provider"),
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response putDataProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("dataProviderId") String dataProviderId,
      @ApiParam DataProvider dataProvider) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    DataProvider dp = applications.getDataProvider(dataProviderId);
    EntityTag etag = new EntityTag(Long.toString(dp.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateDataProvider(dataProviderId, dataProvider);
    etag = new EntityTag(Long.toString(dataProvider.getModified()));
    return Response.noContent()
        .tag(etag)
        .build();
  }

  @PUT
  @Path("/{dataProviderId}/scopes")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Updates a data provider scopes")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested data provider does not exist, or no data provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested data provider"),
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response putDataProviderScopes(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("dataProviderId") String dataProviderId,
      @ApiParam("scopes") Scopes scopes) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }
    DataProvider dp = applications.getDataProvider(dataProviderId);
    if (dp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested data provider does not exist")
          .build();
    }

    Scopes s = applications.getProvidedScopes(dataProviderId);
    EntityTag etag = new EntityTag(Long.toString(s.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateDataProviderScopes(dataProviderId, scopes);
    etag = new EntityTag(Long.toString(scopes.getModified()));
    return Response.noContent()
        .tag(etag)
        .build();
  }

  @DELETE
  @Path("/{dataProviderId}")
  @ApiOperation(value = "Deletes data provider")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested data provider does not exist"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access or delete the requested data provider"),
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response deleteDataProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("dataProviderId") String dataProviderId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    DataProvider dp = applications.getDataProvider(dataProviderId);
    if (dp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested data provider does not exist")
          .build();
    }

    EntityTag etag = new EntityTag(Long.toString(dp.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.deleteDataProvider(dataProviderId);
    return Response.noContent()
        .build();
  }
}
