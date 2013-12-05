package oasis.web.apps;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import oasis.model.applications.Application;
import oasis.model.applications.ApplicationRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;

@Path("/d/app/market")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d/app/market", description = "Application market API")
public class ApplicationMarketEndpoint {
  @Inject
  private ApplicationRepository applications;

  @Inject
  private DirectoryRepository directory;

  @GET
  @ApiOperation(value = "Retrieve available applications",
      notes = "Returns application array",
      response = Application.class,
      responseContainer = "Array")
  public Response getApplications(
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit) {
    return Response.ok()
        .entity(applications.getCatalogApplications(start, limit).iterator())
        .build();
  }

  @POST
  @Path("/d/app/market/{organizationId}/{applicationId}")
  @ApiOperation(value = "Instantiate an application")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested organization/application does not exist, or no organization/application id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The requested application cannot be instantiated"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot perform this operation") })
  public Response instantiateApplication(
      @Context UriInfo uriInfo,
      @PathParam("organizationId") String organizationId,
      @PathParam("applicationId") String applicationId
  ) throws URISyntaxException {
    Application app = applications.getApplication(applicationId);
    Organization org = directory.getOrganization(organizationId);
    if (app == null || org == null){
      return Response.status(Response.Status.NOT_FOUND)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested organization/application does not exist")
          .build();
    }

    if (!Application.ApplicationType.CLASS.equals(app.getApplicationType())) {
      return Response.status(Response.Status.FORBIDDEN)
          .type(MediaType.TEXT_PLAIN)
          .entity("The requested application cannot be instantiated")
          .build();
    }

    Application res = new Application();
    res.setIconUri(app.getIconUri());
    res.setName(app.getName());
    res.setParentApplicationId(applicationId);
    res.setClassAdmin(app.getClassAdmin());
    res.setInstanceAdmin(organizationId);
    res.setExposedInCatalog(false);
    if (Application.InstantiationType.COPY.equals(app.getInstantiationType())) {
      res.setDataProviders(app.getDataProviders());
      res.setServiceProvider(app.getServiceProvider());
    }

    String newAppId = applications.createApplication(res);
    EntityTag etag = new EntityTag(Long.toString(res.getModified()));
    URI uri = new URI(uriInfo.getRequestUri().toString() + newAppId);
    return Response.created(uri)
        .tag(etag)
        .build();
  }
}
