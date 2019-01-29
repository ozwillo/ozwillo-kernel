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
package oasis.web.i18n;

import javax.annotation.Nullable;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Variant;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.ibm.icu.util.ULocale;

import oasis.model.i18n.LocalizableValue;

public class LocaleHelper {

  public static final ULocale DEFAULT_LOCALE = ULocale.FRANCE;

  public static final ImmutableList<ULocale> SUPPORTED_LOCALES = ImmutableList.of(
      ULocale.UK,
      ULocale.FRANCE,
      ULocale.ITALY,
      ULocale.forLanguageTag("bg-BG"),
      ULocale.forLanguageTag("ca-ES"),
      ULocale.forLanguageTag("es-ES"),
      ULocale.forLanguageTag("tr-TR")
  );
  static {
    assert SUPPORTED_LOCALES.contains(DEFAULT_LOCALE);
  }

  private static final LocalizableValue<ULocale> SUPPORTED_LOCALES_MAP;
  static {
    final LocalizableValue<ULocale> supportedLocalesMap = new LocalizableValue<>();

    // Iterate in reverse order so that the first locales take precedence (overwrite)
    for (ULocale supportedLocale : SUPPORTED_LOCALES.reverse()) {
      for (ULocale candidateLocale = supportedLocale; candidateLocale != null; candidateLocale = candidateLocale.getFallback()) {
        if (!candidateLocale.equals(ULocale.ROOT)) {
          supportedLocalesMap.set(candidateLocale, supportedLocale);
        }
      }
    }

    SUPPORTED_LOCALES_MAP = supportedLocalesMap.unmodifiable();
  }

  private static final ImmutableList<Variant> SUPPORTED_LOCALES_VARIANTS;
  static {
    ImmutableList.Builder<Variant> supportedLocalesVariants = ImmutableList.builder();

    for (ULocale supportedLocale : SUPPORTED_LOCALES) {
      supportedLocalesVariants.add(new Variant(null, supportedLocale.toLocale(), null));
    }

    SUPPORTED_LOCALES_VARIANTS = supportedLocalesVariants.build();
  }

  public ULocale selectLocale(Iterable<String> ui_locales, Request request) {
    for (String candidateLocale : ui_locales) {
      ULocale selectedLocale = SUPPORTED_LOCALES_MAP.get(ULocale.forLanguageTag(candidateLocale));
      if (selectedLocale != null) {
        return selectedLocale;
      }
    }
    return selectLocale(request);
  }

  public ULocale selectLocale(Request request) {
    Variant selectedVariant = request.selectVariant(SUPPORTED_LOCALES_VARIANTS);
    if (selectedVariant != null) {
      return ULocale.forLocale(selectedVariant.getLanguage());
    }
    return DEFAULT_LOCALE;
  }

  public ULocale selectLocale(@Nullable ULocale locale, Request request) {
    if (locale != null) {
      locale = SUPPORTED_LOCALES_MAP.get(locale);
    }
    if (locale == null) {
      locale = selectLocale(request);
    }
    return MoreObjects.firstNonNull(locale, DEFAULT_LOCALE);
  }
}
