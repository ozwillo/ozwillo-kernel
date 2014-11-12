package oasis.model.i18n;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class LocalizableValue<T> {
  private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

  final Map<Locale, T> values;

  public LocalizableValue(T rootValue) {
    this();
    set(Locale.ROOT, rootValue);
  }

  public LocalizableValue() {
    this(new HashMap<Locale, T>());
  }

  public LocalizableValue(LocalizableValue<? extends T> src) {
    this(new HashMap<>(src.values));
  }

  protected LocalizableValue(Map<Locale, T> values) {
    this.values = values;
  }

  public T get(Locale locale) {
    for (Locale candidateLocale : control.getCandidateLocales("", locale)) {
      T value = values.get(candidateLocale);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public void set(Locale locale, T localizedValue) {
    values.put(locale, localizedValue);
  }

  public LocalizableValue<T> unmodifiable() {
    return new LocalizableValue<>(Collections.unmodifiableMap(values));
  }
}
