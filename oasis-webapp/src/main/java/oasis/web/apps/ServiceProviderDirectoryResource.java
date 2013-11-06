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
import oasis.model.applications.ScopeCardinalities;
import oasis.model.applications.ServiceProvider;

@Path("/d/app/{applicationId}/serviceproviders")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/app/{applicationId}/serviceproviders", description = "Application service providers directory API")
public class ServiceProviderDirectoryResource {

  @Inject
  private ApplicationRepository applications;

  @PathParam("applicationId")
  private String applicationId;

  @GET
  @ApiOperation(value = "Retrieve service providers of an application",
                notes = "Returns service providers array",
                response = ServiceProvider.class,
                responseContainer = "Array")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application does not exist, or no application id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application") })
  public Response getServiceProviders() {
    Collection<ServiceProvider> serviceProviders = applications.getServiceProviders(applicationId);
    if (serviceProviders != null) {
      return Response.ok()
          .entity(serviceProviders)
          .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application does not exist")
          .build();
    }
  }

  @GET
  @Path("/{serviceProviderId}")
  @ApiOperation(value = "Retrieve a service provider",
                notes = "Returns a service provider",
                response = ServiceProvider.class)
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application/service provider does not exist, or no application/service provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application") })
  public Response getServiceProvider(
      @PathParam("serviceProviderId") String serviceProviderId) {
    ServiceProvider serviceProvider = applications.getServiceProvider(applicationId, serviceProviderId);
    if (serviceProvider != null) {
      EntityTag etag = new EntityTag(Long.toString(serviceProvider.getModified()));
      return Response.ok()
          .entity(serviceProvider)
          .tag(etag)
          .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application/service provider does not exist")
          .build();
    }
  }

  @GET
  @Path("/{serviceProviderId}/scopes")
  @ApiOperation(value = "Retrieve required scopes for a service provider",
                notes = "Returns a ScopeCardinality array",
                response = ScopeCardinalities.class)
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application/service provider does not exist, or no application/service provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application") })
  public Response getServiceProviderScopes(
      @PathParam("serviceProviderId") String serviceProviderId) {
    ScopeCardinalities cardinalities = applications.getRequiredScopes(applicationId, serviceProviderId);
    if (cardinalities != null) {
      EntityTag etag = new EntityTag(Long.toString(cardinalities.getModified()));
      return Response.ok()
          .entity(cardinalities)
          .tag(etag)
          .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application/service provider does not exist")
          .build();
    }
  }

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates a service provider")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application does not exist, or no application id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application") })
  public Response postServiceProvider(
      @Context UriInfo uriInfo,
      @ApiParam ServiceProvider serviceProvider) throws URISyntaxException {
    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application does not exist")
          .build();
    }

    String serviceProviderId = applications.createServiceProvider(applicationId, serviceProvider);
    EntityTag etag = new EntityTag(Long.toString(serviceProvider.getModified()));
    URI res = new URI(uriInfo.getRequestUri().toString() + serviceProviderId);
    return Response.created(res)
        .tag(etag)
        .build();
  }

  @PUT
  @Path("/{serviceProviderId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Updates a service provider")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application does not exist, or no application id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application"),
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response putServiceProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("serviceProviderId") String serviceProviderId,
      @ApiParam ServiceProvider serviceProvider) {
    Application app = applications.getApplication(applicationId);
    if (app == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application does not exist")
          .build();
    }
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    ServiceProvider sp = applications.getServiceProvider(applicationId, serviceProviderId);
    EntityTag etag = new EntityTag(Long.toString(sp.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateServiceProvider(applicationId, serviceProviderId, serviceProvider);
    etag = new EntityTag(Long.toString(serviceProvider.getModified()));
    return Response.noContent()
        .tag(etag)
        .build();
  }

  @PUT
  @Path("/{serviceProviderId}/scopes")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Creates or updates a service provider required scopes")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application/service provider does not exist, or no application/service provider id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested application"),
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response putServiceProviderScopes(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("serviceProviderId") String serviceProviderId,
      @ApiParam ScopeCardinalities scopeCardinalities) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }
    ServiceProvider sp = applications.getServiceProvider(applicationId, serviceProviderId);
    if (sp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application/service provider does not exist")
          .build();
    }

    ScopeCardinalities s = applications.getRequiredScopes(applicationId, serviceProviderId);
    EntityTag etag = new EntityTag(Long.toString(s.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.updateServiceProviderScopes(applicationId, serviceProviderId, scopeCardinalities);
    etag = new EntityTag(Long.toString(scopeCardinalities.getModified()));
    return Response.noContent()
        .tag(etag)
        .build();
  }

  @DELETE
  @Path("/{serviceProviderId}")
  @ApiOperation(value = "Deletes service provider")
  @ApiResponses({ @ApiResponse(code = oasis.web.Application.SC_PRECONDITION_REQUIRED,
                               message = "The If-Match header is mandatory"),
                  @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested application/service provider does not exist, or no application id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access or delete the requested service provider") ,
                  @ApiResponse(code = HttpServletResponse.SC_PRECONDITION_FAILED,
                               message = "Mismatching etag") })
  public Response deleteServiceProvider(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("serviceProviderId") String serviceProviderId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return Response.status(oasis.web.Application.SC_PRECONDITION_REQUIRED).build();
    }

    ServiceProvider sp = applications.getServiceProvider(applicationId, serviceProviderId);
    if (sp == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("The requested application/service provider does not exist")
          .build();
    }

    EntityTag etag = new EntityTag(Long.toString(sp.getModified()));
    Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
    if (responseBuilder != null) {
      return responseBuilder.build();
    }

    applications.deleteServiceProvider(applicationId, serviceProviderId);
    return Response.noContent()
        .build();
  }
}
