package oasis.web.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Locale;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;

public class LocaleParamConverterProvider implements ParamConverterProvider {
  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (Locale.class.equals(rawType)) {
      return (ParamConverter<T>) new LocaleParamConverter();
    }
    return null;
  }

  private static class LocaleParamConverter implements ParamConverter<Locale> {
    @Override
    public Locale fromString(String value) {
      return Locale.forLanguageTag(value);
    }

    @Override
    public String toString(Locale value) {
      return value.toLanguageTag();
    }
  }
}
