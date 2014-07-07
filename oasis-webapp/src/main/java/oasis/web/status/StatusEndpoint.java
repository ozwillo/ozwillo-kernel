package oasis.web.status;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jongo.Jongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.ReadPreference;

import oasis.web.kibana.KibanaModule;

@Path("/status")
public class StatusEndpoint {

  private static final long PING_TIMEOUT_IN_SECONDS = 5;

  @Inject KibanaModule.Settings kibanaSettings;
  @Inject Jongo jongo;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response check() {
    Status status = new Status();

    Future<Response> esStatus = ClientBuilder.newClient()
        .target(UriBuilder.fromUri(kibanaSettings.elasticsearchUrl).path("_nodes"))
        .request().async().get();

    try {
      status.mongodb = jongo.getDatabase()
          .command("ping", ReadPreference.primary())
          .ok();
    } catch (Exception e) {
      status.mongodb = false;
    }

    try {
      status.elasticSearch = esStatus.get(PING_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
          .getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
    } catch (Exception e) {
      status.elasticSearch = false;
    }

    if (status.mongodb && status.elasticSearch) {
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
