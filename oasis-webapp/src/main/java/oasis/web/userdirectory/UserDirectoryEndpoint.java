package oasis.web.userdirectory;

import java.net.URI;
import java.util.Collection;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import oasis.model.InvalidVersionException;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/d")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "/d", description = "User directory API")
public class UserDirectoryEndpoint {

  @Inject DirectoryRepository directory;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  /*
   * Organization
   */

  @POST
  @Path("/org")
  @ApiOperation(value = "Create an organization",
      notes = "The returned location URL get access to the organization (retrieve, update, delete this organization)",
      response = Organization.class)
  public Response createOrganization(@Context SecurityContext securityContext, Organization organization) {
    organization = directory.createOrganization(organization);

    // Automatically add the current user as an admin for the organization
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrganizationId(organization.getId());
    membership.setAccountId(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    membership.setAdmin(true);
    organizationMembershipRepository.createOrganizationMembership(membership);

    // XXX: add link to org membership that we just created?
    URI uri = UriBuilder.fromResource(OrganizationEndpoint.class).build(organization.getId());
    return Response
        .created(uri)
        .contentLocation(uri)
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
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
      return ResponseFactory.notFound("The requested organization does not exist");
    }
    return Response.ok().entity(new GenericEntity<Collection<Group>>(groups) {}).build();
  }

  @POST
  @Path("/org/{organizationId}/groups")
  @ApiOperation(value = "Add a group in an organization",
      notes = "The returned location URL get access to the group (retrieve, update, delete this group)",
      response = Group.class)
  public Response createGroup(@PathParam("organizationId") String organizationId, Group group) {
    Organization organization = directory.getOrganization(organizationId);
    if (organization == null) {
      return ResponseFactory.notFound("The requested organization does not exist");
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
      return ResponseFactory.notFound("The requested group does not exist");
    }
    return Response
        .ok()
        .entity(group)
        .tag(etagService.getEtag(group))
        .build();
  }

  @PUT
  @Path("/group/{groupId}")
  @ApiOperation(value = "Update a group",
      response = Group.class)
  public Response updateGroup(
      @Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("groupId") String groupId,
      Group group) {

    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    Group updatedGroup = null;
    try {
      updatedGroup = directory.updateGroup(groupId, group, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }

    if (updatedGroup == null) {
      return ResponseFactory.notFound("The requested group does not exist");
    }

    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class)
        .path(UserDirectoryEndpoint.class, "getGroup")
        .build(groupId);
    return Response.created(uri)
        .tag(etagService.getEtag(updatedGroup))
        .contentLocation(uri)
        .entity(updatedGroup)
        .build();
  }

  @DELETE
  @Path("/group/{groupId}")
  @ApiOperation(value = "Delete a group")
  public Response deleteGroup(@Context Request request,
      @HeaderParam("If-Match") @ApiParam(required = true) String etagStr,
      @PathParam("groupId") String groupId) {
    if (Strings.isNullOrEmpty(etagStr)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    boolean deleted;
    try {
      deleted = directory.deleteGroup(groupId, etagService.parseEtag(etagStr));
    } catch (InvalidVersionException e) {
      return ResponseFactory.preconditionFailed(e.getMessage());
    }
    if (!deleted) {
      return ResponseFactory.notFound("The requested group does not exist");
    }
    return ResponseFactory.NO_CONTENT;
  }

  @GET
  @Path("/group/{groupId}/organization")
  @ApiOperation(value = "Retrieve the organization of a group",
      notes = "The returned location URL get access to the organization (retrieve, update, delete this organization)",
      response = Organization.class)
  public Response getOrganizationFromGroup(@PathParam("groupId") String groupId) {
    Organization organization = directory.getOrganizationFromGroup(groupId);
    if (organization == null) {
      return ResponseFactory.notFound("The requested group does not exist");
    }
    URI uri = UriBuilder.fromResource(OrganizationEndpoint.class).build(organization.getId());
    return Response
        .ok()
        .contentLocation(uri)
        .entity(organization)
        .tag(etagService.getEtag(organization))
        .build();
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
      return ResponseFactory.notFound("The requested group does not exist");
    }
    return Response
        .ok()
        .entity(new GenericEntity<Collection<String>>(agentIds) {})
        .build();
  }

  @POST
  @Path("/group/{groupId}/members")
  @ApiOperation(value = "Add an agent in a group")
  public Response createGroupMember(@PathParam("groupId") String groupId, String agentId) {
    Group group = directory.getGroup(groupId);
    if (group == null) {
      return ResponseFactory.notFound("The requested group does not exist");
    }
    // TODO: validate agentId, validate agent's organization and groupId
    directory.addGroupMember(groupId, agentId);

    URI uri = UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "removeGroupMember").build(groupId, agentId);
    return Response
        .created(uri)
        .build();
  }

  @DELETE
  @Path("/group/{groupId}/member/{agentId}")
  @ApiOperation(value = "Remove an agent from a group")
  public Response removeGroupMember(@PathParam("groupId") String groupId, @PathParam("agentId") String agentId) {
    if (!directory.removeGroupMember(groupId, agentId)) {
      return ResponseFactory.notFound("The requested group does not exist");
    }

    return ResponseFactory.NO_CONTENT;
  }

  @GET
  @Path("/agent/{agentId}/groups")
  @ApiOperation(value = "Retrieve the groups of an agent",
      response = Group.class,
      responseContainer = "Array")
  public Response getGroupsForAgentAccount(@PathParam("agentId") String agentId) {
    Collection<Group> groups = directory.getGroupsForAgent(agentId);
    if (groups == null) {
      return ResponseFactory.notFound("The requested agent does not exist");
    }
    return Response
        .ok()
        .entity(new GenericEntity<Collection<Group>>(groups) {})
        .build();
  }
}
