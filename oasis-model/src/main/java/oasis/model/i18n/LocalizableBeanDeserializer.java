/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
import java.util.IllformedLocaleException;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.ibm.icu.util.ULocale;

class LocalizableBeanDeserializer extends BeanDeserializer {
  private final Map<String, BeanPropertyDefinition> _propertyDefinitions;

  protected LocalizableBeanDeserializer(LocalizableBeanDeserializer src) {
    super(src);
    this._propertyDefinitions = src._propertyDefinitions;
  }

  protected LocalizableBeanDeserializer(LocalizableBeanDeserializer src, boolean ignoreAllUnknown) {
    super(src, ignoreAllUnknown);
    this._propertyDefinitions = src._propertyDefinitions;
  }

  protected LocalizableBeanDeserializer(LocalizableBeanDeserializer src, NameTransformer unwrapper) {
    super(src, unwrapper);
    this._propertyDefinitions = src._propertyDefinitions;
  }

  public LocalizableBeanDeserializer(LocalizableBeanDeserializer src, ObjectIdReader oir) {
    super(src, oir);
    this._propertyDefinitions = src._propertyDefinitions;
  }

  public LocalizableBeanDeserializer(LocalizableBeanDeserializer src, Set<String> ignorableProps) {
    super(src, ignorableProps);
    this._propertyDefinitions = src._propertyDefinitions;
  }

  public LocalizableBeanDeserializer(BeanDeserializerBase builder, Map<String, BeanPropertyDefinition> properties) {
    super(builder);
    this._propertyDefinitions = properties;
  }

  @Override
  public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
    return new LocalizableBeanDeserializer(this, unwrapper);
  }

  @Override
  public BeanDeserializer withIgnorableProperties(Set<String> ignorableProps) {
    return new LocalizableBeanDeserializer(this, ignorableProps);
  }

  @Override
  public BeanDeserializer withObjectIdReader(ObjectIdReader oir) {
    return new LocalizableBeanDeserializer(this, oir);
  }

  @Override
  protected void handleUnknownVanilla(JsonParser jp, DeserializationContext ctxt, Object bean, String propName)
      throws IOException, JsonProcessingException {
    int hash = propName.lastIndexOf('#');
    if (hash > 0) {
      SettableBeanProperty prop = findProperty(propName.substring(0, hash));
      if (prop != null && prop.getType().hasRawClass(LocalizableString.class)) {
        try {
          ULocale locale = parseLocale(propName.substring(hash + 1));
          LocalizableString ls = getValue(ctxt, bean, prop);
          ls.set(locale, jp.getValueAsString());
          return;
        } catch (IllformedLocaleException ife) {
          // fall through
        }
      }
    }
    super.handleUnknownVanilla(jp, ctxt, bean, propName);
  }

  private ULocale parseLocale(String languageTag) throws IllformedLocaleException {
    return new ULocale.Builder()
        .setLanguageTag(languageTag)
        .build();
  }

  private LocalizableString getValue(DeserializationContext ctxt, Object bean, SettableBeanProperty prop) {
    BeanPropertyDefinition propDef = _propertyDefinitions.get(prop.getName());
    AnnotatedMember accessor = propDef.getAccessor();
    if (ctxt.canOverrideAccessModifiers()) {
      accessor.fixAccess(true);
    }
    LocalizableString ls = (LocalizableString) accessor.getValue(bean);
    if (ls == null) {
      ls = new LocalizableString();
      accessor.setValue(bean, ls);
    }
    return ls;
  }
}
