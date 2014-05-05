package oasis.model.i18n;

import java.util.List;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

public class LocalizableBeanSerializerModifier extends BeanSerializerModifier {
  @Override
  public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
    for (int i = 0; i < beanProperties.size(); i++) {
      final BeanPropertyWriter writer = beanProperties.get(i);
      if (LocalizableString.class.isAssignableFrom(writer.getPropertyType())) {
        beanProperties.set(i, new LocalizableStringBeanPropertyWriter(writer));
      }
    }
    return super.changeProperties(config, beanDesc, beanProperties);
  }
}
