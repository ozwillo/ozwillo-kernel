package oasis.model.i18n;

import java.io.IOException;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.util.NameTransformer;

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

  public LocalizableBeanDeserializer(LocalizableBeanDeserializer src, HashSet<String> ignorableProps) {
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
  public BeanDeserializer withIgnorableProperties(HashSet<String> ignorableProps) {
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
          Locale locale = parseLocale(propName.substring(hash + 1));
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

  private Locale parseLocale(String languageTag) throws IllformedLocaleException {
    return new Locale.Builder()
        .setLanguageTag(languageTag)
        .build();
  }

  private LocalizableString getValue(DeserializationContext ctxt, Object bean, SettableBeanProperty prop) {
    BeanPropertyDefinition propDef = _propertyDefinitions.get(prop.getName());
    AnnotatedMember accessor = propDef.getAccessor();
    if (ctxt.canOverrideAccessModifiers()) {
      accessor.fixAccess();
    }
    LocalizableString ls = (LocalizableString) accessor.getValue(bean);
    if (ls == null) {
      ls = new LocalizableString();
      accessor.setValue(bean, ls);
    }
    return ls;
  }
}