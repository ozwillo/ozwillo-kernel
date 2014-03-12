package oasis.web.authn;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import oasis.model.authn.ClientType;
import oasis.services.authn.CredentialsService;

/**
 * Implements HTTP Basic authentication, used when client applications authenticate to some OAuth 2.0 endpoints.
 *
 * @see <a href='http://tools.ietf.org/html/rfc2617'>HTTP Authentication: Basic and Digest Access Authentication</a>
 * @see <a href='http://tools.ietf.org/html/draft-ietf-httpbis-p7-auth'>HTTP/1.1: Authentication (httpbis)</a>
 */
@Authenticated @Client
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ClientAuthenticationFilter implements ContainerRequestFilter {

  // cf. http://greenbytes.de/tech/webdav/draft-ietf-httpauth-basicauth-enc-latest.html
  // NOTE: we only expect to generate US_ASCII credentials
  private static final Charset CREDENTIALS_ENCODING = StandardCharsets.UTF_8;
  // TODO: make realm configurable
  private static final String CHALLENGE = String.format(
      "%s realm=\"OASIS Client applications\", charset=\"%s\"",
      SecurityContext.BASIC_AUTH, CREDENTIALS_ENCODING.name());

  private static final Splitter AUTH_SCHEME_SPLITTER = Splitter.on(' ').omitEmptyStrings();
  private static final Splitter CREDENTIALS_SPLITTER = Splitter.on(':').limit(2);

  @Inject CredentialsService credentialsService;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if (Strings.isNullOrEmpty(authorization)) {
      challenge(requestContext);
      return;
    }

    List<String> parts = AUTH_SCHEME_SPLITTER.splitToList(authorization);

    if (parts.size() != 2 || !SecurityContext.BASIC_AUTH.equalsIgnoreCase(parts.get(0))) {
      challenge(requestContext);
      return;
    }

    String credentials;
    try {
      CharsetDecoder charsetDecoder = CREDENTIALS_ENCODING.newDecoder();
      credentials = charsetDecoder.decode(ByteBuffer.wrap(BaseEncoding.base64().decode(parts.get(1)))).toString();
    } catch (CharacterCodingException | IllegalArgumentException e) {
      malformedCredentials(requestContext);
      return;
    }
    parts = CREDENTIALS_SPLITTER.splitToList(credentials);
    if (parts.size() != 2) {
      malformedCredentials(requestContext);
      return;
    }

    final String clientId = parts.get(0);
    final String clientSecret = parts.get(1);

    // TODO: Authenticate the client

    if (Strings.isNullOrEmpty(clientId)) {
      challenge(requestContext);
      return;
    }

    if (!credentialsService.checkPassword(ClientType.PROVIDER, clientId, clientSecret)) {
      challenge(requestContext);
    }

    final ClientPrincipal clientPrincipal = new ClientPrincipal(clientId);

    final SecurityContext oldSecurityContext = requestContext.getSecurityContext();
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public ClientPrincipal getUserPrincipal() {
        return clientPrincipal;
      }

      @Override
      public boolean isUserInRole(String role) {
        return false;
      }

      @Override
      public boolean isSecure() {
        return oldSecurityContext.isSecure();
      }

      @Override
      public String getAuthenticationScheme() {
        return SecurityContext.BASIC_AUTH;
      }
    });
  }

  private void challenge(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE)
        .build());
  }

  private void malformedCredentials(ContainerRequestContext requestContext) {
    requestContext.abortWith(Response
        .status(Response.Status.BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity("Malformed credentials")
        .build());
  }
}
