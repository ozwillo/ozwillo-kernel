package oasis.model.i18n;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.ibm.icu.util.ULocale;

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
    ret.set(ULocale.ROOT, jp.getValueAsString());
    return ret;
  }
}
