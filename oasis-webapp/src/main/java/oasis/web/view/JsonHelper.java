package oasis.web.view;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

public class JsonHelper implements Helper<Object> {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .registerModule(new JodaModule())
      .registerModule(new GuavaModule())
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  @Override
  public CharSequence apply(Object context, Options options) throws IOException {
    return new Handlebars.SafeString(OBJECT_MAPPER.writeValueAsString(context));
  }
}
