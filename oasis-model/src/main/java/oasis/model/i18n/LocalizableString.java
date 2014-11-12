package oasis.model.i18n;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = LocalizableStringSerializer.class)
@JsonDeserialize(using = LocalizableStringDeserializer.class)
public class LocalizableString extends LocalizableValue<String> {

  public LocalizableString(String rootValue) {
    super(rootValue);
  }

  public LocalizableString() {
    super();
  }

  public LocalizableString(LocalizableString src) {
    super(src);
  }

  private LocalizableString(Map<Locale, String> values) {
    super(values);
  }

  @Override
  public LocalizableString unmodifiable() {
    return new LocalizableString(Collections.unmodifiableMap(values));
  }
}
