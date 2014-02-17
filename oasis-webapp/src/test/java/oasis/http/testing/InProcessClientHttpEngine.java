package oasis.http.testing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.util.CaseInsensitiveMap;

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
    mockRequest.getMutableHeaders().putAll(request.getHeaders().asMap());
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
    return mockRequest;
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
    response.setHeaders(transformHeaders(mockResponse.getOutputHeaders()));

    return response;
  }

  private MultivaluedMap<String, String> transformHeaders(MultivaluedMap<String, Object> outputHeaders) {
    MultivaluedMap<String, String> headers = new CaseInsensitiveMap<String>();
    for (Map.Entry<String, List<Object>> header : outputHeaders.entrySet()) {
      for (Object value : header.getValue()) {
        headers.add(header.getKey(), dispatcher.getProviderFactory().toHeaderString(value));
      }
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
}
