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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

@NotThreadSafe
public class LocalizableValue<T> {

  protected final boolean modifiable;
  final Map<ULocale, T> values;

  private @Nullable LocaleMatcher localeMatcher;

  public LocalizableValue(T rootValue) {
    this();
    set(ULocale.ROOT, rootValue);
  }

  public LocalizableValue() {
    this.modifiable = true;
    this.values = new LinkedHashMap<>();
  }

  public LocalizableValue(LocalizableValue<? extends T> src) {
    this(src, src.modifiable);
  }

  protected LocalizableValue(LocalizableValue<? extends T> src, boolean modifiable) {
    this.modifiable = modifiable;
    this.values = modifiable ? new LinkedHashMap<>(src.values) : Collections.unmodifiableMap(src.values);
    this.localeMatcher = src.localeMatcher;
  }

  public T get(ULocale locale) {
    if (values.isEmpty()) {
      return null;
    }
    T value = values.get(locale);
    if (value != null) {
      return value;
    }
    return values.get(ensureLocaleMatcher().getBestMatch(locale));
  }

  public void set(ULocale locale, T localizedValue) {
    if (!this.modifiable) {
      throw new UnsupportedOperationException();
    }
    values.put(locale, localizedValue);
    localeMatcher = null;
  }

  public LocalizableValue<T> unmodifiable() {
    return modifiable ? new LocalizableValue<>(this, true) : this;
  }

  @Nonnull
  private LocaleMatcher ensureLocaleMatcher() {
    assert !this.values.isEmpty();
    LocaleMatcher localeMatcher = this.localeMatcher;
    if (localeMatcher == null) {
      Iterator<ULocale> localesIterator = values.keySet().iterator();
      LocalePriorityList.Builder builder = LocalePriorityList.add(values.containsKey(ULocale.ROOT) ? ULocale.ROOT : localesIterator.next());
      while (localesIterator.hasNext()) {
        ULocale locale = localesIterator.next();
        if (ULocale.ROOT.equals(locale)) {
          continue; // Don't add ROOT twice, it would no longer be first
        }
        builder.add(locale);
      }
      this.localeMatcher = localeMatcher = new LocaleMatcher(builder.build());
    }
    return localeMatcher;
  }
}
