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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.AbstractDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

public class LocalizableBeanDeserializerModifier extends BeanDeserializerModifier {

  @Override
  public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, final BeanDescription beanDesc, BeanDeserializerBuilder builder) {
    for (Iterator<SettableBeanProperty> it = builder.getProperties(); it.hasNext(); ) {
      SettableBeanProperty prop = it.next();
      if (LocalizableString.class.isAssignableFrom(prop.getType().getRawClass())) {
        return new BeanDeserializerBuilder(builder) {
          @Override
          public JsonDeserializer<?> build() {
            Map<String, BeanPropertyDefinition> propertyDefinitions = new LinkedHashMap<>();
            for (BeanPropertyDefinition prop : beanDesc.findProperties()) {
              propertyDefinitions.put(prop.getName(), prop);
            }
            return new LocalizableBeanDeserializer((BeanDeserializerBase) super.build(), propertyDefinitions);
          }

          @Override
          public AbstractDeserializer buildAbstract() {
            throw new UnsupportedOperationException();
          }

          @Override
          public JsonDeserializer<?> buildBuilderBased(JavaType valueType, String expBuildMethodName) {
            throw new UnsupportedOperationException();
          }
        };
      }
    }
    return builder;
  }
}
