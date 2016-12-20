/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
