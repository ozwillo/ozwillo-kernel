package oasis.model.i18n;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class LocalizableModule extends SimpleModule {
  public LocalizableModule() {
    setSerializerModifier(new LocalizableBeanSerializerModifier());
    setDeserializerModifier(new LocalizableBeanDeserializerModifier());
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
