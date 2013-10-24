package oasis.web.providers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;

import org.jboss.resteasy.core.interception.PreMatchContainerRequestContext;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;

import com.google.common.base.Splitter;

/**
 * Workaround for https://issues.jboss.org/browse/RESTEASY-961, re-parse cookies where Resteasy failed.
 * <p>
 * Also use the <a href="http://tools.ietf.org/html/rfc6265">RFC 6265</a> parsing algorithm.
 */
@PreMatching
public class CookieParserRequestFilter implements ContainerRequestFilter {

  private static final Splitter COOKIE_LIST_SPLITTER = Splitter.on(';').omitEmptyStrings().trimResults();

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    PreMatchContainerRequestContext context = (PreMatchContainerRequestContext) requestContext;
    ResteasyHttpHeaders headers = (ResteasyHttpHeaders) context.getHttpRequest().getHttpHeaders();

    Map<String, Cookie> cookies = new LinkedHashMap<String, Cookie>();
    for (String cookieString : headers.getRequestHeader(HttpHeaders.COOKIE)) {
      for (String cookiePair : COOKIE_LIST_SPLITTER.split(cookieString)) {
        Cookie cookie = Cookie.valueOf(cookiePair);
        cookies.put(cookie.getName(), cookie);
      }
    }
    headers.setCookies(cookies);
  }
}
