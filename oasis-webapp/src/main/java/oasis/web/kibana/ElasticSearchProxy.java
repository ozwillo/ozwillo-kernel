package oasis.web.kibana;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.name.Named;

// TODO: more specific path see https://github.com/elasticsearch/kibana/blob/master/sample/apache_ldap.conf
@Path("/es{path: (/.*)?}")
public class ElasticSearchProxy {

  @Inject
  @Named(KibanaModule.ELASTICSEARCH)
  private WebTarget elasticsearchWebTarget;

  @Context
  private UriInfo uriInfo;

  @GET
  public Response get(@PathParam("path") String path) throws ExecutionException, InterruptedException {
    SettableFuture<Response> future = SettableFuture.create();

    getWebTarget(path).request().async().get(getInvocationCallback(future));

    // XXX: handle exception
    return future.get();
  }

  @POST
  public Response post(@PathParam("path") String path, String data) throws ExecutionException, InterruptedException {
    SettableFuture<Response> future = SettableFuture.create();

    getWebTarget(path).request().async().post(Entity.text(data), getInvocationCallback(future));

    // XXX: handle exception
    return future.get();
  }

  private InvocationCallback<Response> getInvocationCallback(final SettableFuture<Response> future) {
    return new InvocationCallback<Response>() {
      @Override
      public void completed(Response response) {
        future.set(buildResponse(response));
      }

      @Override
      public void failed(Throwable throwable) {
        future.setException(throwable);
      }
    };
  }

  private Response buildResponse(Response response) {
    // Status
    Response.ResponseBuilder responseBuilder = Response.status(response.getStatus());
    // Headers
    for (Map.Entry<String, List<Object>> header : response.getHeaders().entrySet()) {
      for (Object o : header.getValue()) {
        responseBuilder.header(header.getKey(), o);
      }
    }
    // Entity
    if (response.bufferEntity()) {
      responseBuilder.entity(response.readEntity(InputStream.class));
    } else {
      if (response.hasEntity()) {
        responseBuilder.entity(response.getEntity());
      }
    }
    return responseBuilder.build();
  }

  private WebTarget getWebTarget(String path) {
    WebTarget target = elasticsearchWebTarget;

    // Handle path
    if (!Strings.isNullOrEmpty(path)) {
      target = target.path(path);
    }
    // Handle params
    for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters(true).entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue().toArray());
    }
    return target;
  }
}
