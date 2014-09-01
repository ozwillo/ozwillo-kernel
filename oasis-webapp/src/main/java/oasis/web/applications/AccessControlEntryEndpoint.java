package oasis.web.applications;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Strings;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.utils.ResponseFactory;

@Path("/apps/acl/ace/{ace_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
@Api(value = "acl-ace", description = "Access Control Entry")
public class AccessControlEntryEndpoint {
  @Inject AccessControlRepository accessControlRepository;
  @Inject EtagService etagService;

  @PathParam("ace_id") String ace_id;

  @GET
  @ApiOperation(
      value = "Retrieves an ACE",
      response = AccessControlEntry.class
  )
  public Response get() {
    AccessControlEntry ace = accessControlRepository.getAccessControlEntry(ace_id);
    if (ace == null) {
      return ResponseFactory.NOT_FOUND;
    }
    return Response.ok()
        .tag(etagService.getEtag(ace))
        .entity(ace)
        .build();
  }

  @DELETE
  @ApiOperation("Deletes an ACE")
  public Response revoke(@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch) {
    if (Strings.isNullOrEmpty(ifMatch)) {
      return ResponseFactory.preconditionRequiredIfMatch();
    }

    // TODO: only admins can delete ACEs

    boolean deleted;
    try {
      deleted = accessControlRepository.deleteAccessControlEntry(ace_id, etagService.parseEtag(ifMatch));
    } catch (InvalidVersionException ive) {
      return ResponseFactory.preconditionFailed(ive.getMessage());
    }

    if (!deleted) {
      return ResponseFactory.NOT_FOUND;
    }

    return ResponseFactory.NO_CONTENT;
  }
}
