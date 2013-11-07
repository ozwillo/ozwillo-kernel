package com.atolcd.logging.log4j.fluentd;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.core.appender.AbstractManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

public class FluentdManager extends AbstractManager {

  private static final Logger logger = LoggerFactory.getLogger(FluentdManager.class);
  private final String url;
  private final String tag;

  private FluentdManager(String name, String url, String tag) {
    super(name);

    this.url = url;
    this.tag = tag;
  }

  public static FluentdManager getFluentdManager(String name, String url, String tag) {
    return new FluentdManager(name, url, tag);
  }

  public void recordEvent(String json) {
    Client client = ClientBuilder.newClient();
    Escaper escaper = UrlEscapers.urlPathSegmentEscaper();
    String jsonEscaped = escaper.escape(json);
    WebTarget target = client.target(url + "/" + tag).queryParam("json", jsonEscaped);
    try {
      Response response = target.request().post(null);
      if (!response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
        logger.error("The request to the fluentd server has failed : {}", response.readEntity(String.class));
      }
    } catch(ProcessingException e) {
      logger.error("The audit event can't reach the fluentd server.", e);
    }
  }
}
