package oasis.web.view;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

import com.google.template.soy.tofu.SoyTofu;

import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;

public class SoyTemplateBodyWriter implements MessageBodyWriter<SoyTemplate> {

  @Inject SoyTemplateRenderer soyTemplateRenderer;

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return SoyTemplate.class.isAssignableFrom(type);
  }

  @Override
  public long getSize(SoyTemplate view, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(SoyTemplate view, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws
      IOException {
    String encoding = mediaType.getParameters().get(MediaType.CHARSET_PARAMETER);
    if (encoding == null || encoding.isEmpty()) {
      encoding = StandardCharsets.UTF_8.name();
      httpHeaders.putSingle(HttpHeaders.CONTENT_TYPE, mediaType.withCharset(encoding));
    }

    OutputStreamWriter outputWriter = new OutputStreamWriter(entityStream, encoding);
    soyTemplateRenderer.render(view, outputWriter);
    outputWriter.flush();
  }
}
