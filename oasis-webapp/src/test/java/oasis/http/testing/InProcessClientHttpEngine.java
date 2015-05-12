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
package oasis.http.testing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.util.CaseInsensitiveMap;
import org.jboss.resteasy.util.CookieParser;

public class InProcessClientHttpEngine implements ClientHttpEngine {
  private final Dispatcher dispatcher;
  private final URI baseUri;

  private SSLContext sslContext;
  private HostnameVerifier hostnameVerifier;

  public InProcessClientHttpEngine(Dispatcher dispatcher, URI baseUri) {
    this.dispatcher = dispatcher;
    this.baseUri = baseUri;
  }

  @Override
  public ClientResponse invoke(ClientInvocation request) {
    MockHttpRequest mockRequest = createRequest(request);

    MockHttpResponse mockResponse = new MockHttpResponse();
    dispatcher.invoke(mockRequest, mockResponse);

    return createResponse(request, mockResponse);
  }

  private MockHttpRequest createRequest(ClientInvocation request) {
    MockHttpRequest mockRequest = MockHttpRequest.create(request.getMethod(), request.getUri(), baseUri);

  if (request.getEntity() != null) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    request.getDelegatingOutputStream().setDelegate(baos);
    try {
      request.writeRequestBody(request.getEntityStream());
      baos.close();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    mockRequest.setInputStream(new ByteArrayInputStream(baos.toByteArray()));
  }

    MultivaluedMap<String, String> requestHeaders = request.getHeaders().asMap();
    mockRequest.getMutableHeaders().putAll(requestHeaders);
    Map<String, Cookie> cookies = extractCookies(requestHeaders);
    mockRequest.setCookies(cookies);

    return mockRequest;
  }

  private Map<String, Cookie> extractCookies(MultivaluedMap<String, String> requestHeaders) {
    Map<String, Cookie> cookies = new HashMap<>();
    List<String> cookieHeaders = requestHeaders.get(HttpHeaders.COOKIE);
    if (cookieHeaders == null) {
      return cookies;
    }

    for (String cookieHeader : cookieHeaders) {
      for (Cookie cookie : CookieParser.parseCookies(cookieHeader)) {
        cookies.put(cookie.getName(), cookie);
      }
    }
    return cookies;
  }

  private ClientResponse createResponse(final ClientInvocation request, final MockHttpResponse mockResponse) {
    ClientResponse response = new ClientResponse(request.getClientConfiguration()) {
      private InputStream inputStream;

      @Override
      protected InputStream getInputStream() {
        if (inputStream == null) {
          inputStream = new ByteArrayInputStream(mockResponse.getOutput());
        }
        return inputStream;
      }

      @Override
      protected void setInputStream(InputStream is) {
        inputStream = is;
      }

      @Override
      protected void releaseConnection() throws IOException {
        // no-op
      }
    };

    response.setStatus(mockResponse.getStatus());
    response.setHeaders(transformHeaders(mockResponse.getOutputHeaders(), mockResponse.getNewCookies()));

    return response;
  }

  private MultivaluedMap<String, String> transformHeaders(MultivaluedMap<String, Object> outputHeaders, List<NewCookie> newCookies) {
    MultivaluedMap<String, String> headers = new CaseInsensitiveMap<String>();
    for (Map.Entry<String, List<Object>> header : outputHeaders.entrySet()) {
      for (Object value : header.getValue()) {
        headers.add(header.getKey(), dispatcher.getProviderFactory().toHeaderString(value));
      }
    }
    for (NewCookie newCookie : newCookies) {
      headers.add(HttpHeaders.SET_COOKIE, dispatcher.getProviderFactory().toHeaderString(newCookie));
    }
    return headers;
  }

  @Override
  public SSLContext getSslContext() {
    return sslContext;
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  @Override
  public HostnameVerifier getHostnameVerifier() {
    return hostnameVerifier;
  }

  public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
    this.hostnameVerifier = hostnameVerifier;
  }

  @Override
  public void close() {
    // no-op
  }

  /**
   * Workaround for https://issues.jboss.org/browse/RESTEASY-1020
   */
  private static class MockHttpRequest extends org.jboss.resteasy.mock.MockHttpRequest {
    public static MockHttpRequest create(String httpMethod, URI uri, URI baseUri) {
      org.jboss.resteasy.mock.MockHttpRequest toCopy = org.jboss.resteasy.mock.MockHttpRequest.create(httpMethod, uri, baseUri);
      MockHttpRequest ret = new MockHttpRequest();
      ret.uri = toCopy.getUri();
      ret.httpHeaders = (ResteasyHttpHeaders) toCopy.getHttpHeaders();
      ret.httpMethod = toCopy.getHttpMethod();
      ret.inputStream = toCopy.getInputStream();
      return ret;
    }

    @Deprecated
    @Override
    public org.jboss.resteasy.mock.MockHttpRequest cookie(String name, String value) {
      return super.cookie(name, value);
    }

    public void setCookies(Map<String, Cookie> cookies) {
      httpHeaders.setCookies(cookies);
    }
  }
}
