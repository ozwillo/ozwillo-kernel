package oasis.web.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;

import com.ibm.icu.util.ULocale;

public class LocaleParamConverterProvider implements ParamConverterProvider {
  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (ULocale.class.equals(rawType)) {
      return (ParamConverter<T>) new LocaleParamConverter();
    }
    return null;
  }

  private static class LocaleParamConverter implements ParamConverter<ULocale> {
    @Override
    public ULocale fromString(String value) {
      return ULocale.forLanguageTag(value);
    }

    @Override
    public String toString(ULocale value) {
      return value.toLanguageTag();
    }
  }
}
