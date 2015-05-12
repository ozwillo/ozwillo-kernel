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
