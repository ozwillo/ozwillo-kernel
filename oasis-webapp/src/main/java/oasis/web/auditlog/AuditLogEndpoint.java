/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
