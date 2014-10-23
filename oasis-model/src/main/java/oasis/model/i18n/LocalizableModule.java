package oasis.model.i18n;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

public class LocalizableModule extends SimpleModule {
  public LocalizableModule() {
    setSerializerModifier(new LocalizableBeanSerializerModifier());
    setDeserializerModifier(new LocalizableBeanDeserializerModifier());

    addSerializer(Locale.class, new StdScalarSerializer<Locale>(Locale.class) {
      @Override
      public void serialize(Locale value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
        jgen.writeString(value.toLanguageTag());
      }
    });
    addDeserializer(Locale.class, new FromStringDeserializer<Locale>(Locale.class) {
      @Override
      protected Locale _deserialize(String value, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return Locale.forLanguageTag(value);
      }
    });
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }
}
