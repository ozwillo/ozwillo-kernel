package oasis.web.userdirectory;

import java.net.URI;
import java.util.Collection;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.services.etag.EtagService;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.AgentAccount;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.directory.Organization;

/*
 * TODO: etag
 * TODO: authorization
 */
@Path("/d")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/d", description = "User directory API")
public class UserDirectoryEndpoint {

  @Inject
  private DirectoryRepository directory;

  @Inject
  private AccountRepository account;

  @Inject
  private EtagService etagService;

  /*
   * Organization
   */

  @POST
  @Path("/org")
  @ApiOperation(value = "Create an organization",
      notes = "The returned location URL get access to the organization (retrieve, update, delete this organization)",
      response = Organization.class)
  public Response createOrganization(Organization organization) {
    Organization res = directory.createOrganization(organization);
    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "getOrganization").build(res.getId());
    return Response
        .created(uri)
        .contentLocation(uri)
        .entity(res)
        .tag(etagService.getEtag(res))
        .build();
  }

  @GET
  @Path("/org/{organizationId}")
  @ApiOperation(value = "Retrieve an organization",
      response = Organization.class)
  public Response getOrganization(@PathParam("organizationId") String organizationId) {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }
    return Response
        .ok()
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
  }

  @PUT
  @Path("/org/{organizationId}")
  @ApiOperation(value = "Update an organization")
  public Response updateOrganization(@PathParam("organizationId") String organizationId, Organization organization) {
    if (directory.getOrganization(organizationId) == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }
    // TODO: check error and/or returned type
    directory.updateOrganization(organizationId, organization);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/org/{organizationId}")
  @ApiOperation(value = "Delete an organization")
  public Response deleteOrganization(@PathParam("organizationId") String organizationId) {
    // TODO: check error and/or returned type
    if (!directory.deleteOrganization(organizationId)) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }

    // XXX: refactor ?
    account.deleteAgentAccountsFromOrganization(organizationId);

    return Response.noContent().build();
  }

  /*
   * Group
   */
  @GET
  @Path("/org/{organizationId}/groups")
  @ApiOperation(value = "Retrieve groups of an organization",
      notes = "Returns groups array",
      response = Group.class,
      responseContainer = "Array")
  public Response getGroups(@PathParam("organizationId") String organizationId) {
    Collection<Group> groups = directory.getGroups(organizationId);
    if (groups == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }
    return Response.ok().entity(groups).build();
  }

  @POST
  @Path("/org/{organizationId}/groups")
  @ApiOperation(value = "Add a group in an organization",
      notes = "The returned location URL get access to the group (retrieve, update, delete this group)",
      response = Group.class)
  public Response createGroup(@PathParam("organizationId") String organizationId, Group group) {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }
    Group res = directory.createGroup(organizationId, group);
    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "getGroup").build(res.getId());
    return Response
        .created(uri)
        .contentLocation(uri)
        .entity(res)
        .tag(etagService.getEtag(res))
        .build();
  }

  @GET
  @Path("/group/{groupId}")
  @ApiOperation(value = "Retrieve a group",
      response = Group.class)
  public Response getGroup(@PathParam("groupId") String groupId) {
    Group group = directory.getGroup(groupId);
    if (group == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    return Response
        .ok()
        .entity(group)
        .tag(etagService.getEtag(group))
        .build();
  }

  @PUT
  @Path("/group/{groupId}")
  @ApiOperation(value = "Update a group")
  public Response updateGroup(@PathParam("groupId") String groupId, Group group) {
    if (directory.getGroup(groupId) == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    // TODO: check error and/or returned type
    directory.updateGroup(groupId, group);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/group/{groupId}")
  @ApiOperation(value = "Delete a group")
  public Response deleteGroup(@PathParam("groupId") String groupId) {
    if (!directory.deleteGroup(groupId)) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    return Response.noContent().build();
  }

  @GET
  @Path("/group/{groupId}/organization")
  @ApiOperation(value = "Retrieve the organization of a group",
      notes = "The returned location URL get access to the organization (retrieve, update, delete this organization)",
      response = Organization.class)
  public Response getOrganizationFromGroup(@PathParam("groupId") String groupId) {
    Organization organization = directory.getOrganizationFromGroup(groupId);
    if (organization == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "getOrganization").build(organization.getId());
    return Response
        .ok()
        .contentLocation(uri)
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
  }

  /*
   * AgentAccount
   */
  @GET
  @Path("/org/{organizationId}/agents")
  @ApiOperation(value = "Retrieve agents of an organization",
      notes = "Returns agents array",
      response = AgentAccount.class,
      responseContainer = "Array")
  public Response getAgentAccounts(@PathParam("organizationId") String organizationId,
      @DefaultValue("0") @QueryParam("start") int start,
      @DefaultValue("25") @QueryParam("limit") int limit) {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }

    Iterable<AgentAccount> agents = account.getAgentsForOrganization(organizationId, start, limit);
    return Response.ok().entity(agents).build();
  }

  @POST
  @Path("/org/{organizationId}/agents")
  @ApiOperation(value = "Add an agent in an organization",
      notes = "The returned location URL get access to the agent (retrieve, delete this agent)",
      response = AgentAccount.class)
  public Response createAgentAccount(@PathParam("organizationId") String organizationId, AgentAccount agent) {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested organization does not exist")
          .build();
    }
    String agentId = account.createAgentAccount(organizationId, agent);
    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "getAgentAccount").build(agentId);
    return Response.created(uri).contentLocation(uri).entity(agent).build();
  }

  @GET
  @Path("/group/{groupId}/members")
  @ApiOperation(value = "Retrieve members of a group",
      notes = "Returns agents id array",
      response = String.class,
      responseContainer = "Array")
  public Response getMembers(@PathParam("groupId") String groupId) {
    Collection<String> agentIds = directory.getGroupMembers(groupId);
    if (agentIds == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    return Response.ok().entity(agentIds).build();
  }

  @POST
  @Path("/group/{groupId}/members")
  @ApiOperation(value = "Add an agent in a group")
  public Response createGroupMember(@PathParam("groupId") String groupId, String agentId) {
    Group group = directory.getGroup(groupId);
    if (group == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }
    directory.addGroupMember(groupId, agentId);

    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "removeGroupMember").build(groupId, agentId);
    return Response.created(uri).build();
  }

  @DELETE
  @Path("/group/{groupId}/member/{agentId}")
  @ApiOperation(value = "Remove an agent from a group")
  public Response removeGroupMember(@PathParam("groupId") String groupId, @PathParam("agentId") String agentId) {
    if (!directory.removeGroupMember(groupId, agentId)) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested group does not exist")
          .build();
    }

    return Response.noContent().build();
  }

  @GET
  @Path("/agent/{agentId}/groups")
  @ApiOperation(value = "Retrieve the groups of an agent",
      response = Group.class,
      responseContainer = "Array")
  public Response getGroupsForAgentAccount(@PathParam("agentId") String agentId) {
    Collection<Group> groups = directory.getGroupsForAgent(agentId);
    if (groups == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested agent does not exist")
          .build();
    }
    return Response.ok().entity(groups).build();
  }

  @GET
  @Path("/agent/{agentId}")
  @ApiOperation(value = "Retrieve an agent",
      response = AgentAccount.class)
  public Response getAgentAccount(@PathParam("agentId") String agentId) {
    AgentAccount agent = account.getAgentAccountById(agentId);
    if (agent == null) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested agent does not exist")
          .build();
    }
    return Response.ok().entity(agent).build();
  }

  @DELETE
  @Path("/agent/{agentId}")
  @ApiOperation(value = "Delete an agent")
  public Response deleteAgentAccount(@PathParam("agentId") String agentId) {
    if (!account.deleteAgentAccount(agentId)) {
      return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
          .entity("The requested agent does not exist")
          .build();
    }
    return Response.noContent().build();
  }
}
