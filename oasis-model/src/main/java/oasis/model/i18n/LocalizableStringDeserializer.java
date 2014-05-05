package oasis.model.i18n;

import java.io.IOException;
import java.util.IllformedLocaleException;
import java.util.Locale;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Simple deserializer for {@link LocalizableString} that only deals with the root locale.
 *
 * <p>Other locales are parsed by the {@link LocalizableBeanDeserializer}.
 */
class LocalizableStringDeserializer extends StdDeserializer<LocalizableString> {

  public LocalizableStringDeserializer() {
    super(LocalizableString.class);
  }

  @Override
  public LocalizableString deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    LocalizableString ret = new LocalizableString();
    ret.set(Locale.ROOT, jp.getValueAsString());
    return ret;
  }
}
