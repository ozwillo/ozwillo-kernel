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
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

class LocalizableStringSerializer extends JsonSerializer<LocalizableString> implements ContextualSerializer {
  private final String propertyName;

  LocalizableStringSerializer() {
    this(null);
  }

  LocalizableStringSerializer(@Nullable String propertyName) {
    this.propertyName = propertyName;
  }

  @Override @Deprecated
  public boolean isEmpty(LocalizableString value) {
    if (value == null || value.values.isEmpty()) {
      return true;
    }
    for (String localizedValue : value.values.values()) {
      if (localizedValue != null && !localizedValue.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isEmpty(SerializerProvider provider, LocalizableString value) {
    return isEmpty(value);
  }

  @Override
  public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
    JsonUnwrapped unwrapped = property.getMember().getAnnotation(JsonUnwrapped.class);
    if (unwrapped == null || !unwrapped.enabled()) {
      return this;
    }
    return new LocalizableStringSerializer(property.getName());
  }

  @Override
  public boolean isUnwrappingSerializer() {
    return propertyName != null;
  }

  @Override
  public void serialize(LocalizableString value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
    for (Map.Entry<Locale, String> entry : value.values.entrySet()) {
      String localizedValue = entry.getValue();

      if (localizedValue == null || localizedValue.isEmpty()) {
        continue;
      }

      String key = LocalizableStringHelper.serializeKey(propertyName, entry.getKey());
      jgen.writeStringField(key, localizedValue);
    }
  }
}
