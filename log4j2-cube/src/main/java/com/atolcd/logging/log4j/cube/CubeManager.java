package com.atolcd.logging.log4j.cube;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.core.appender.AbstractManager;

public class CubeManager extends AbstractManager {

  private final String url;

  private CubeManager(String name, String url) {
    super(name);

    this.url = url;
  }

  public static CubeManager getCubeManager(String name, String url) {
    return new CubeManager(name, url);
  }

  public void recordEvent(String json) {
    Client client = ClientBuilder.newClient();
    WebTarget target = client.target(url + "/1.0/event/put");
    try {
      Response response = target.request().post(Entity.json(json));
      if (!response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
        LOGGER.error("The request to the cube server has failed : {}", response.readEntity(String.class));
      }
    } catch (ProcessingException e) {
      LOGGER.error("The audit event can't reach the cube server.", e);
    }
  }
}
