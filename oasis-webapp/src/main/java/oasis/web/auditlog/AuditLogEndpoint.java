package oasis.web.auditlog;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.auditlog.AuditLogService;
import oasis.auditlog.RemoteAuditLogEvent;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.utils.ResponseFactory;

@Path("/l")
@Authenticated @Client
@Api(value = "/l", description = "Audit log API")
public class AuditLogEndpoint {

  @Inject AuditLogService auditLogService;

  @Path("/event")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Log an event in the audit log service")
  public Response json(RemoteEvent remoteEvent) {

    // XXX: generate AuditLogEvent.eventType from remote application ?
    auditLogService.event(RemoteAuditLogEvent.class, remoteEvent.time)
        .setLog(remoteEvent.log)
        .log();

    return ResponseFactory.NO_CONTENT;
  }
}
