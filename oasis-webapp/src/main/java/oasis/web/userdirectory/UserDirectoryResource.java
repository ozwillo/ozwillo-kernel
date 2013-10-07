package oasis.web.userdirectory;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import oasis.model.accounts.AgentAccount;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;

@Path("/d")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d", description = "User directory API")
public class UserDirectoryResource {

  @Inject
  private DirectoryRepository directory;

  @GET
  @Path("/org/{organizationId}/members")
  @ApiOperation(value = "Retrieve members of an organization",
                notes = "Returns agents array",
                response = AgentAccount.class,
                responseContainer = "Array")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested organization does not exist, or no organization id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested organization") })
  public Response getAccounts(@PathParam("organizationId") String organizationId) {
    Collection<AgentAccount> accounts = directory.getOrganizationMembers(organizationId);
    if (accounts != null){
      return Response.ok()
              .entity(accounts)
              .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("The requested organization does not exist")
              .build();
    }
  }

  @GET
  @Path("/org/{organizationId}/groups")
  @ApiOperation(value = "Retrieve groups of an organization",
                notes = "Returns groups array",
                response = Group.class,
                responseContainer = "Array")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested organization does not exist, or no organization id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested organization") })
  public Response getGroups(@PathParam("organizationId") String organizationId) {
    Collection<Group> groups = directory.getGroups(organizationId);
    if (groups != null){
      return Response.ok()
              .entity(groups)
              .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("The requested organization does not exist")
              .build();
    }
  }

  @GET
  @Path("/group/{groupId}/members")
  @ApiOperation(value = "Retrieve members of a group",
                notes = "Returns agents array",
                response = AgentAccount.class,
                responseContainer = "Array")
  @ApiResponses({ @ApiResponse(code = HttpServletResponse.SC_NOT_FOUND,
                               message = "The requested group does not exist, or no group id has been sent"),
                  @ApiResponse(code = HttpServletResponse.SC_FORBIDDEN,
                               message = "The current user cannot access the requested group") })
  public Response getMembers(@PathParam("groupId") String groupId) {
    Collection<AgentAccount> agents = directory.getGroupMembers(groupId);
    if (agents != null){
      return Response.ok()
              .entity(agents)
              .build();
    } else {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("The requested group does not exist")
              .build();
    }
  }
}
