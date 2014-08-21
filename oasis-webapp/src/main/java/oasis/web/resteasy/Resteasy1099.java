package oasis.web.resteasy;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/**
 * Workaround for https://issues.jboss.org/browse/RESTEASY-1099
 */
public class Resteasy1099 {
  public static URI getBaseUri(UriInfo uriInfo) {
    return getBaseUriBuilder(uriInfo).build();
  }

  public static UriBuilder getBaseUriBuilder(UriInfo uriInfo) {
    return uriInfo.getBaseUriBuilder().replaceQuery(null);
  }
}
