package oasis.web.webhooks;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Priority;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;

/**
 * Compute an {@code X-Hub-Signature} request header from a shared secret and request body.
 *
 * @see <a href="https://pubsubhubbub.googlecode.com/git/pubsubhubbub-core-0.4.html#authednotify">
 *   PubSubHubbub's Authenticated Content Distribution</a>
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class WebhookSignatureFilter implements WriterInterceptor {
  private static final String ALGORITHM = "HmacSHA1";
  @VisibleForTesting public static final String HEADER = "X-Hub-Signature";

  private final String secret;

  public WebhookSignatureFilter(String secret) {
    this.secret = secret;
  }

  @Override
  public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
    if (secret == null || secret.trim().isEmpty()) {
      // TODO: log error?
      return;
    }

    final SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    final Mac mac;
    try {
      mac = Mac.getInstance(ALGORITHM);
      mac.init(secretKeySpec);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // This shouldn't happen: HmacSHA1 is a mandatory-to-implement algorithm, and doesn't restrict its keys
      throw Throwables.propagate(e);
    }

    // We need to buffer all the output to be able to add the header
    OutputStream realOut = context.getOutputStream();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    context.setOutputStream(new FilterOutputStream(baos) {
      @Override
      public void write(int b) throws IOException {
        mac.update((byte) b);
        out.write(b);
      }

      @Override
      public void write(byte[] b) throws IOException {
        mac.update(b);
        out.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        mac.update(b, off, len);
        out.write(b, off, len);
      }
    });

    context.proceed();

    byte[] signature = mac.doFinal();
    context.getHeaders().putSingle(HEADER, "sha1=" + BaseEncoding.base16().encode(signature));

    realOut.write(baos.toByteArray());
  }
}
