/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.web.status;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jongo.Jongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.ReadPreference;

import oasis.elasticsearch.ElasticsearchModule;

@Path("/status")
public class StatusEndpoint {

  private static final long PING_TIMEOUT_IN_SECONDS = 5;

  @Inject Client client;
  @Inject ElasticsearchModule.Settings esSettings;
  @Inject Jongo jongo;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response check() {
    Status status = new Status();

    Future<Response> esStatus = client
        .target(esSettings.url())
        .request().async().head();

    try {
      status.mongodb = jongo.getDatabase()
          .command("ping", ReadPreference.primary())
          .ok();
    } catch (Exception e) {
      status.mongodb = false;
    }

    try {
      Response response = esStatus.get(PING_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
      try {
        status.elasticSearch = response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
      } finally {
        response.close();
      }
    } catch (Exception e) {
      status.elasticSearch = false;
    }

    if (status.mongodb) {
      return Response.ok(status).build();
    } else {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(status).build();
    }
  }

  public static class Status {
    @JsonProperty boolean mongodb;
    @JsonProperty boolean elasticSearch;
  }
}
