package oasis.model.i18n;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;

public class LocalizableStringBeanPropertyWriter extends BeanPropertyWriter {
  public LocalizableStringBeanPropertyWriter(BeanPropertyWriter writer) {
    super(writer);
  }

  @Override
  public void serializeAsField(Object bean, JsonGenerator jgen, SerializerProvider prov) throws Exception {
    LocalizableString value = (LocalizableString) get(bean);
    // Null handling is bit different, check that first
    if (value == null) {
      if (_nullSerializer != null) {
        jgen.writeFieldName(_name);
        _nullSerializer.serialize(null, jgen, prov);
      }
      return;
    }
    // then find serializer to use
    JsonSerializer<Object> ser = _serializer;
    if (ser == null) {
      Class<?> cls = value.getClass();
      PropertySerializerMap map = _dynamicSerializers;
      ser = map.serializerFor(cls);
      if (ser == null) {
        ser = _findAndAddDynamic(map, cls, prov);
      }
    }
    // and then see if we must suppress certain values (default, empty)
    if (_suppressableValue != null) {
      if (MARKER_FOR_EMPTY == _suppressableValue) {
        if (ser.isEmpty(value)) {
          return;
        }
      } else if (_suppressableValue.equals(value)) {
        return;
      }
    }
    // For non-nulls: simple check for direct cycles
    if (value == bean) {
      _handleSelfReference(bean, jgen, prov, ser);
    }

    for (Map.Entry<Locale, String> entry : value.values.entrySet()) {
      Locale locale = entry.getKey();
      String localizedValue = entry.getValue();

      if (localizedValue == null || localizedValue.isEmpty()) {
        continue;
      }

      String key;
      if (Locale.ROOT.equals(locale)) {
        key = _name.toString();
      } else {
        key = _name + "#" + locale.toLanguageTag();
      }
      jgen.writeStringField(key, localizedValue);
    }
  }
}
