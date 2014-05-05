package oasis.web.view;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Jackson2Helper;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import oasis.model.i18n.LocalizableModule;

public class HandlebarsBodyWriter implements MessageBodyWriter<View> {
  private static final Handlebars HANDLEBARS = new Handlebars(new ClassPathTemplateLoader())
      .registerHelper("json", new Jackson2Helper(new ObjectMapper()
          .registerModule(new JodaModule())
          .registerModule(new GuavaModule())
          .registerModule(new LocalizableModule())
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)))
      .startDelimiter("[[")
      .endDelimiter("]]");

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return View.class.isAssignableFrom(type);
  }

  @Override
  public long getSize(View view, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(View view, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
    String encoding = mediaType.getParameters().get(MediaType.CHARSET_PARAMETER);
    if (encoding == null || encoding.isEmpty()) {
      encoding = StandardCharsets.UTF_8.name();
      httpHeaders.putSingle(HttpHeaders.CONTENT_TYPE, mediaType.withCharset(encoding));
    }

    Context context = Context.newBuilder(view.getModel()).build();

    try {
      OutputStreamWriter outputWriter = new OutputStreamWriter(entityStream, encoding);
      HANDLEBARS.compile(view.getPath()).apply(context, outputWriter);
      outputWriter.flush();
    } finally {
      context.destroy();
    }
  }
}
