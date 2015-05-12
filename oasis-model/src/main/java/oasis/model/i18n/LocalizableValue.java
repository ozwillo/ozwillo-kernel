/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.model.i18n;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.ibm.icu.util.ULocale;

public class LocalizableValue<T> {
  private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

  final Map<Locale, T> values;

  public LocalizableValue(T rootValue) {
    this();
    set(ULocale.ROOT, rootValue);
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

  public T get(ULocale locale) {
    for (Locale candidateLocale : control.getCandidateLocales("", locale.toLocale())) {
      T value = values.get(candidateLocale);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public void set(ULocale locale, T localizedValue) {
    values.put(locale.toLocale(), localizedValue);
  }

  public LocalizableValue<T> unmodifiable() {
    return new LocalizableValue<>(Collections.unmodifiableMap(values));
  }
}
