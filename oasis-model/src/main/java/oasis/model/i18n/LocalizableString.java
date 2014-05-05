package oasis.model.i18n;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = LocalizableStringSerializer.class)
@JsonDeserialize(using = LocalizableStringDeserializer.class)
public class LocalizableString {
  private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

  final Map<Locale, String> values;

  public LocalizableString(String rootValue) {
    this();
    set(Locale.ROOT, rootValue);
  }

  public LocalizableString() {
    this(new HashMap<Locale, String>());
  }

  public LocalizableString(LocalizableString src) {
    this(new HashMap<>(src.values));
  }

  private LocalizableString(Map<Locale, String> values) {
    this.values = values;
  }

  public String get(Locale locale) {
    for (Locale candidateLocale : control.getCandidateLocales("", locale)) {
      String value = values.get(candidateLocale);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public void set(Locale locale, String localizedValue) {
    values.put(locale, localizedValue);
  }

  public LocalizableString unmodifiable() {
    return new LocalizableString(Collections.unmodifiableMap(values));
  }
}
