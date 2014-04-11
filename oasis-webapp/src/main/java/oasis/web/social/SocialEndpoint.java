package oasis.web.social;

import java.net.URI;
import java.util.Collection;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.social.IdentityRepository;
import oasis.services.etag.EtagService;
import oasis.web.utils.ResponseFactory;

/*
 * TODO: authorization
 */
@Path("/s")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/s", description = "Social API")
public class SocialEndpoint {

  @Inject
  private IdentityRepository identities;

  @Inject
  private EtagService etagService;

  @GET
  @Path("/identity/{identityId}/relationTypes")
  @ApiOperation(value = "Retrieve relationTypes of a identity",
      response = String.class,
      responseContainer = "Array")
  public Response getRelationTypes(@PathParam("identityId") String identityId) {
    Collection<String> types = identities.getRelationTypes(identityId);
    if (types == null) {
      return ResponseFactory.notFound("The requested identity does not exist");
    }
    return Response
        .ok()
        .entity(new GenericEntity<Collection<String>>(types) {})
        .build();
  }

  @GET
  @Path("/identity/{identityId}/relation/{relationType}/members")
  @ApiOperation(value = "Retrieve members of a relation by identityId an relationType",
      response = String.class,
      responseContainer = "Array")
  public Response getRelationMembers(@PathParam("identityId") String identityId, @PathParam("relationType") String relationType) {
    Collection<String> members = identities.getRelationMembers(identityId, relationType);
    if (members == null) {
      return ResponseFactory.notFound("The requested identity does not exist");
    }
    return Response
        .ok()
        .entity(new GenericEntity<Collection<String>>(members) {})
        .build();
  }

  @POST
  @Path("/identity/{identityId}/relation/{relationType}/members")
  @ApiOperation(value = "Add a relation between two identities",
      notes = "The returned location URL get access to the relation (delete this relation)")
  public Response createRelation(@PathParam("identityId") String identityId, @PathParam("relationType") String relationType,
      String destIdentityId) {

    boolean created = identities.addRelation(identityId, relationType, destIdentityId);
    if (!created) {
      return ResponseFactory.notFound("The requested identity does not exist");
    }
    URI uri = UriBuilder.fromResource(SocialEndpoint.class).path(SocialEndpoint.class, "removeRelation")
        .build(identityId, relationType, destIdentityId);
    return Response
        .created(uri)
        .build();
  }

  @DELETE
  @Path("/identity/{identityId}/relation/{relationType}/members/{destIdentityId}")
  @ApiOperation(value = "Remove a relation")
  public Response removeRelation(@PathParam("identityId") String identityId, @PathParam("relationType") String relationType,
      @PathParam("destIdentityId") String destIdentityId) {
    boolean deleted = identities.removeRelation(identityId, relationType, destIdentityId);
    if (!deleted) {
      return ResponseFactory.notFound("The requested relation does not exist");
    }
    return Response
        .noContent()
        .build();
  }

  @GET
  @Path("/identity/{identityId}/relation/{relationType}/members/{destIdentityId}")
  @ApiOperation(value = "Return true if destIdentityId is related to identityId by relationType",
      response = Boolean.class)
  public Response checkRelation(@PathParam("identityId") String identityId, @PathParam("relationType") String relationType,
      @PathParam("destIdentityId") String destIdentityId) {
    boolean inRelations = identities.relationExists(identityId, relationType, destIdentityId);
    return Response
        .ok()
        .entity(inRelations)
        .build();
  }

  @GET
  @Path("/identity/{identityId}/relation/{relationType}")
  @ApiOperation(value = "Return the relationId identified by an identityId by relationType",
      response = String.class)
  public Response getRelationId(@PathParam("identityId") String identityId, @PathParam("relationType") String relationType) {
    String relationId = identities.getRelationId(identityId, relationType);
    return Response
        .ok()
        .type(MediaType.TEXT_PLAIN)
        .entity(relationId)
        .build();
  }

}
