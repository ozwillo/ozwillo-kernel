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
package oasis.model.i18n;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.ibm.icu.util.ULocale;

public class LocalizableModule extends SimpleModule {
  public LocalizableModule() {
    setSerializerModifier(new LocalizableBeanSerializerModifier());
    setDeserializerModifier(new LocalizableBeanDeserializerModifier());

    addSerializer(ULocale.class, new StdScalarSerializer<ULocale>(ULocale.class) {
      @Override
      public void serialize(ULocale value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
        jgen.writeString(value.toLanguageTag());
      }
    });
    addDeserializer(ULocale.class, new FromStringDeserializer<ULocale>(ULocale.class) {
      @Override
      protected ULocale _deserialize(String value, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return ULocale.forLanguageTag(value);
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
