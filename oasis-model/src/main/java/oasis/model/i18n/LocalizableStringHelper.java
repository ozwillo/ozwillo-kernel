package oasis.model.i18n;

import java.util.Locale;

import javax.annotation.Nullable;

import com.ibm.icu.util.ULocale;

public class LocalizableStringHelper {
  public static String serializeKey(@Nullable String propertyName, ULocale locale) {
    return serializeKey(propertyName, locale.toLocale());
  }

  public static String serializeKey(@Nullable String propertyName, Locale locale) {
    if (Locale.ROOT.equals(locale)) {
      if (propertyName == null || propertyName.isEmpty()) {
        return "_";
      } else {
        return propertyName;
      }
    } else {
      if (propertyName == null || propertyName.isEmpty()) {
        return locale.toLanguageTag();
      } else {
        return propertyName + "#" + locale.toLanguageTag();
      }
    }
  }
}
