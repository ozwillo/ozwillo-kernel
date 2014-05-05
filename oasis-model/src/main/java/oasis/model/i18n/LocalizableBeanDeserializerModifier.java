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
