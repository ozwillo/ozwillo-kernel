package oasis.web;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;

import oasis.audit.AuditService;
import oasis.audit.RemoteLogEvent;

@Path("/l")
@Api(value = "/l", description = "Audit log API")
public class Audit {

  @Inject
  AuditService auditService;

  @Path("/event")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Log an event in the audit log service")
  public Response json(RemoteEvent remoteEvent) {

    // XXX: generate LogEvent.eventType from remote application ?
    auditService.event(RemoteLogEvent.class, remoteEvent.time)
        .setLog(remoteEvent.log)
        .log();

    return Response.noContent().build();
  }

  @ApiModel
  static class RemoteEvent {
    @JsonProperty()
    @ApiModelProperty(required = true)
    Instant time;

    @JsonProperty()
    @ApiModelProperty(required = true)
    ObjectNode log;
  }
}
